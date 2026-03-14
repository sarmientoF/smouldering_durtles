/*
 * Copyright 2019-2020 Ernst Jan Plugge <rmc@dds.nl>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.smouldering_durtles.wk.activities;

import static com.smouldering_durtles.wk.Constants.NO_API_KEY_HELP_DOCUMENT;
import static com.smouldering_durtles.wk.util.ObjectSupport.safe;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smouldering_durtles.wk.GlobalSettings;
import com.smouldering_durtles.wk.R;
import com.smouldering_durtles.wk.db.Converters;
import com.smouldering_durtles.wk.proxy.ViewProxy;
import com.smouldering_durtles.wk.util.AsyncTask;
import com.smouldering_durtles.wk.util.Logger;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;

/**
 * A simple activity only used as a helper to get the user to supply an API key.
 *
 * <p>
 *     As long as no valid API key is present, other activities force this one to
 *     be launched.
 * </p>
 */
public final class NoApiKeyHelpActivity extends AbstractActivity {
    private static final Logger LOGGER = Logger.get(NoApiKeyHelpActivity.class);

    private final ViewProxy saveButton = new ViewProxy();
    private final ViewProxy apiKey = new ViewProxy();

    /**
     * The constructor.
     */
    public NoApiKeyHelpActivity() {
        super(R.layout.activity_no_api_key_help, R.menu.no_api_key_help_options_menu);
    }

    @Override
    protected void onCreateLocal(final @Nullable Bundle savedInstanceState) {
        saveButton.setDelegate(this, R.id.saveButton);
        apiKey.setDelegate(this, R.id.apiKey);

        final ViewProxy document = new ViewProxy(this, R.id.document);
        document.setTextHtml(NO_API_KEY_HELP_DOCUMENT);
        document.setLinkMovementMethod();

        saveButton.setOnClickListener(v -> saveApiKey());

        apiKey.setOnEditorActionListener((v, actionId, event) -> safe(false, () -> {
            if (event == null && actionId == EditorInfo.IME_ACTION_DONE) {
                saveApiKey();
                return true;
            }
            if (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                saveApiKey();
                return true;
            }
            return false;
        }));
    }

    @Override
    protected void onResumeLocal() {
        //
    }

    @Override
    protected void onPauseLocal() {
        //
    }

    @Override
    protected void enableInteractionLocal() {
        saveButton.enableInteraction();
    }

    @Override
    protected void disableInteractionLocal() {
        saveButton.disableInteraction();
    }

    @Override
    protected boolean showWithoutApiKey() {
        return true;
    }

    /**
     * Handler for the save button. Save the API key that was entered.
     * If a custom API URL is configured, performs a migration call to exchange
     * the WaniKani API key for a custom backend token before proceeding.
     */
    private void saveApiKey() {
        safe(() -> {
            if (!interactionEnabled) {
                return;
            }
            disableInteraction();
            final String key = apiKey.getText();
            GlobalSettings.Api.setApiKey(key);
            final String customApiUrl = GlobalSettings.Api.getApiUrl();
            if (!customApiUrl.equals("https://api.wanikani.com/v2")) {
                new AsyncTask<String>() {
                    @Override
                    protected @Nullable String doInBackground() {
                        return safe(null, () -> {
                            final String migrateUrl = customApiUrl + "/migrate";
                            final ObjectMapper mapper = Converters.getObjectMapper();
                            final String requestBody = "{\"wanikani_api_key\":\"" + key + "\"}";
                            LOGGER.info("Posting migration call to: %s", migrateUrl);
                            final URL url = new URL(migrateUrl);
                            final HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
                            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                            connection.setRequestMethod("POST");
                            connection.setDoInput(true);
                            connection.setDoOutput(true);
                            connection.setAllowUserInteraction(false);
                            connection.setConnectTimeout(10000);
                            connection.setReadTimeout(30000);
                            try (final OutputStream os = connection.getOutputStream()) {
                                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
                            }
                            connection.getHeaderFields();
                            LOGGER.info("Migration response code: %d", connection.getResponseCode());
                            try (final InputStream is = connection.getInputStream()) {
                                final JsonNode responseNode = mapper.readTree(is);
                                if (responseNode.has("token")) {
                                    return responseNode.get("token").asText();
                                }
                            }
                            return null;
                        });
                    }

                    @Override
                    protected void onPostExecute(final @Nullable String token) {
                        safe(() -> {
                            if (token != null && !token.isEmpty()) {
                                GlobalSettings.Api.setApiToken(token);
                                LOGGER.info("Custom backend token stored successfully");
                            }
                            goToMainActivity();
                        });
                    }

                    @Override
                    protected void onProgressUpdate(final Object[] values) {
                        //
                    }
                }.execute();
            } else {
                goToMainActivity();
            }
        });
    }
}
