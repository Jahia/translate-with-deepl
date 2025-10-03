package org.jahia.community.translation.deepl.initializers;

import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.initializers.ChoiceListInitializer;
import org.jahia.services.content.nodetypes.initializers.ChoiceListValue;
import org.jahia.services.content.nodetypes.initializers.ModuleChoiceListInitializer;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component(service = ModuleChoiceListInitializer.class)
public class GlossaryLangsInitializer implements ModuleChoiceListInitializer {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryLangsInitializer.class);

    private String key;

    @Reference
    DeepLTranslatorService service;

    public GlossaryLangsInitializer() {
        setKey("glossaryLangs");
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public List<ChoiceListValue> getChoiceListValues(ExtendedPropertyDefinition epd, String param, List<ChoiceListValue> values, Locale locale, Map<String, Object> context) {
        return service.getGlossaryManager().getGlossarySupportedLanguages().stream()
                .map(l -> new ChoiceListValue(l, l))
                .collect(Collectors.toList());
    }
}
