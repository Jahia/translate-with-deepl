package org.jahia.community.translation.deepl.service.impl;

import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TranslationData {

    private static final Logger logger = LoggerFactory.getLogger(TranslationData.class);

    private final Map<String, String> texts = new HashMap<>();
    private final Map<String, String> duplicates = new HashMap<>();
    private final Map<String, String> copiedReferences = new HashMap<>();

    public boolean hasTextToTranslate() {
        return !MapUtils.isEmpty(texts);
    }

    public boolean hasTextToCopy() {
        return !MapUtils.isEmpty(copiedReferences);
    }

    public boolean hasTextToWrite() {
        return hasTextToTranslate() || hasTextToCopy();
    }

    public Map<String, String> getTexts() {
        return Collections.unmodifiableMap(texts);
    }

    public void trackText(String key, String text) {
        final Optional<Map.Entry<String, String>> duplicate = texts.entrySet().stream()
                .filter(e -> StringUtils.equals(text, e.getValue()))
                .findFirst();
        if (duplicate.isPresent()) {
            duplicates.put(key, duplicate.get().getKey());
        } else {
            texts.put(key, text);
        }
    }

    public Map<String, String> completeTranslations(Map<String, String> translations) {
        duplicates.forEach((key, ref) -> translations.put(key, translations.get(ref)));
        translations.putAll(copiedReferences);
        return translations;
    }

    public void trackCopiedValue(String key, String text) {
        copiedReferences.put(key, text);
    }

}
