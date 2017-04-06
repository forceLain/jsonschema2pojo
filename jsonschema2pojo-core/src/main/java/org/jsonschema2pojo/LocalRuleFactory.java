/**
 * Copyright © 2010-2014 Nokia
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jsonschema2pojo;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.codemodel.JClassContainer;
import com.sun.codemodel.JType;
import org.jsonschema2pojo.rules.Rule;
import org.jsonschema2pojo.rules.RuleFactory;
import org.jsonschema2pojo.util.URLUtil;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class LocalRuleFactory extends RuleFactory {
    private static final String URL_PROPERTY = "json_schema_url";
    private final LocalContentResolver localContentResolver;
    private String urlToHandle;

    public LocalRuleFactory() {
        super();
        SchemaStore schemaStore = new SchemaStore();
        this.localContentResolver = new LocalContentResolver();
        schemaStore.contentResolver = localContentResolver;
        setSchemaStore(schemaStore);
        //E.g. https://raw.githubusercontent.com/washingtonpost/ans-schema/master/src/main/resources/schema/ans/0.5.8/
        urlToHandle = System.getenv(URL_PROPERTY);
        if (urlToHandle == null) {
            urlToHandle = System.getProperty(URL_PROPERTY);
        }
    }

    @Override
    public void setGenerationConfig(GenerationConfig config) {
        super.setGenerationConfig(config);

        if (urlToHandle == null) {
            return;
        }

        for (Iterator<URL> sources = config.getSource(); sources.hasNext(); ) {
            URL source = sources.next();
            String dirString = source.toString();
            if (URLUtil.parseProtocol(dirString) == URLProtocol.FILE && URLUtil.getFileFromURL(source).isDirectory()) {
                localContentResolver.registerLocalCopy(urlToHandle, dirString);
            }
        }
    }

    @Override
    public Rule<JClassContainer, JType> getEnumRule() {
        return new CustomEnumRule(super.getEnumRule());
    }

    static class LocalContentResolver extends ContentResolver {

        private static final Map<String, String> LOCAL_MAP = new HashMap<String, String>();

        @Override
        public JsonNode resolve(URI uri) {
            String localCopy = localCopy(uri.toString());
            if (localCopy != null) {
                uri = URI.create(localCopy);
            }
            return super.resolve(uri);
        }

        public void registerLocalCopy(String url, String dir) {
            LOCAL_MAP.put(url, dir);
        }

        private String localCopy(String url) {
            for (Map.Entry<String, String> entry : LOCAL_MAP.entrySet()) {
                if (url.startsWith(entry.getKey())) {
                    return url.replace(entry.getKey(), entry.getValue());
                }
            }
            return null;
        }
    }

    private class CustomEnumRule implements Rule<JClassContainer, JType> {
        private final Rule<JClassContainer, JType> normalEnumRule;

        CustomEnumRule(Rule<JClassContainer, JType> enumRule) {
            this.normalEnumRule = enumRule;
        }

        @Override
        public JType apply(String nodeName, JsonNode node, JClassContainer generatableType, Schema currentSchema) {
            if (nodeName.equals("type")) {
                return asString(nodeName, node, generatableType, currentSchema);
            }
            return normalEnumRule.apply(nodeName, node, generatableType, currentSchema);
        }

        private JType asString(String nodeName, JsonNode node, JClassContainer generatableType, Schema currentSchema) {
            return generatableType.owner().ref(String.class);
        }
    }
}
