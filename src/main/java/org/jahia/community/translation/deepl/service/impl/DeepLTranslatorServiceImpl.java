package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLException;
import com.deepl.api.TextResult;
import com.deepl.api.Translator;
import com.deepl.api.TranslatorOptions;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.service.DeepLTranslationResponse;
import org.jahia.community.translation.deepl.service.DeepLTranslatorService;
import org.jahia.osgi.BundleUtils;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionFactory;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.nodetypes.ExtendedPropertyDefinition;
import org.jahia.services.content.nodetypes.SelectorType;
import org.jahia.utils.LanguageCodeConverters;
import org.jahia.utils.i18n.Messages;
import org.osgi.framework.FrameworkUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.PropertyIterator;
import javax.jcr.PropertyType;
import javax.jcr.RepositoryException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.jahia.community.translation.deepl.DeeplConstants.PROP_API_KEY;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_DO_NOT_CONSIDER_PUBLICATION_STATUS;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_PREFIX_TARGET_LANGUAGES;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_FULLNAME;
import static org.jahia.community.translation.deepl.DeeplConstants.SERVICE_CONFIG_FILE_NAME;
import static org.jahia.community.translation.deepl.DeeplConstants.SUBTREE_ITERABLE_TYPES;

@Component(service = DeepLTranslatorService.class, configurationPid = SERVICE_CONFIG_FILE_NAME)
public class DeepLTranslatorServiceImpl implements DeepLTranslatorService {

    private static final Logger logger = LoggerFactory.getLogger(DeepLTranslatorServiceImpl.class);
    private static final String SLASH = "/";
    private static final String MISSING_RESOURCE = "???";
    private static final Predicate<JCRNodeWrapper> PAGE_NODES_EXCLUSION_PREDICATE = node -> {
        try {
            return !node.isNodeType(Constants.JAHIANT_PAGE);
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    };
    private static final Predicate<JCRNodeWrapper> ALL_NODES_PREDICATE = node -> Boolean.TRUE;

    private Translator translator;
    private final Map<String, String> targetLanguages = new HashMap<>();
    private boolean checkPendingModifications = true;

    private enum PropertyAction {TRANSLATE, COPY, IGNORE}

    @Activate
    public void activate(Map<String, ?> properties) {
        translator = null;
        targetLanguages.clear();
        if (properties == null) {
            logger.error("Missing configurations: {}", SERVICE_CONFIG_FILE_FULLNAME);
            return;
        }

        final String authKey = (String) properties.getOrDefault(PROP_API_KEY, null);
        logger.debug("{} = {}", PROP_API_KEY, authKey);
        translator = initializeTranslator(authKey);
        if (translator == null) return;

        final String doNotConsiderPublicationStatus = (String) properties.getOrDefault(PROP_DO_NOT_CONSIDER_PUBLICATION_STATUS, null);
        checkPendingModifications = !Boolean.parseBoolean(doNotConsiderPublicationStatus);

        properties.entrySet().stream()
                .filter(e -> e.getKey().startsWith(PROP_PREFIX_TARGET_LANGUAGES))
                .forEach(e -> targetLanguages.put(e.getKey().substring(PROP_PREFIX_TARGET_LANGUAGES.length()), (String) e.getValue()));

    }

    private Translator initializeTranslator(String authKey) {
        if (StringUtils.isBlank(authKey)) {
            logger.error("{} not defined. Please add it to {}", PROP_API_KEY, SERVICE_CONFIG_FILE_FULLNAME);
            return null;
        }

        final TranslatorOptions options = new TranslatorOptions().setMaxRetries(3).setTimeout(Duration.ofSeconds(3));

        final String proxyHost = System.getProperty("https.proxyHost");
        final String proxyPort = System.getProperty("https.proxyPort");
        if (proxyHost != null && proxyPort != null) {
            final SocketAddress address = new InetSocketAddress(proxyHost, Integer.parseInt(proxyPort));
            final Proxy proxy = new Proxy(Proxy.Type.HTTP, address);
            options.setProxy(proxy);
        }

        return new Translator(authKey, options);
    }

    @Override
    public DeepLTranslationResponse translate(JCRNodeWrapper pNode, boolean translateSubtree, String sourceLanguage, String targetLanguage, boolean allLanguages, Locale responseLocale) throws RepositoryException {
        final JCRSessionWrapper pNodeSession = pNode.getSession();
        if (!StringUtils.equals(pNodeSession.getWorkspace().getName(), Constants.EDIT_WORKSPACE)) {
            throw new IllegalArgumentException("The translation source must be in the " + Constants.EDIT_WORKSPACE + " workspace");
        }
        final Locale pNodeSrcLocale = pNodeSession.getLocale();
        final String pNodeSrcLanguage = LanguageCodeConverters.localeToLanguageTag(pNodeSrcLocale);
        final Set<String> siteLanguages = pNode.getResolveSite().getLanguages();
        if (!allLanguages) {
            if (!siteLanguages.contains(targetLanguage)) {
                return new DeepLTranslationResponseImpl(false, getText("targetLangNotActivated", responseLocale, new Locale(targetLanguage).getDisplayLanguage(responseLocale)));
            } else if (StringUtils.equals(sourceLanguage, targetLanguage)) {
                return new DeepLTranslationResponseImpl(false, getText("srcAncTargetLangEqual", responseLocale, new Locale(targetLanguage).getDisplayLanguage(responseLocale)));
            }
        }

        final JCRNodeWrapper node;
        final JCRSessionWrapper session;
        if (!StringUtils.equals(sourceLanguage, pNodeSrcLanguage)) {
            session = JCRSessionFactory.getInstance().getCurrentUserSession(pNodeSession.getWorkspace().getName(), LanguageCodeConverters.languageCodeToLocale(sourceLanguage));
            final String path = pNode.getPath();
            if (!session.nodeExists(path)) {
                return new DeepLTranslationResponseImpl(false, getText("srcLangMissingOnNode", responseLocale, new Locale(sourceLanguage).getDisplayLanguage(responseLocale)));
            }
            node = session.getNode(path);
        } else {
            node = pNode;
            session = pNodeSession;
        }

        final TranslationData data = new TranslationData();
        scanTexts(node, translateSubtree, PAGE_NODES_EXCLUSION_PREDICATE, data);

        if (allLanguages) {
            return siteLanguages.stream()
                    .filter(language -> !StringUtils.equals(language, sourceLanguage))
                    .map(lang -> translateAndSave(data, sourceLanguage, lang, responseLocale))
                    .reduce(new DeepLTranslationResponseImpl(false, null), DeepLTranslationResponse::merge);
        } else {
            return translateAndSave(data, sourceLanguage, targetLanguage, responseLocale);
        }
    }

    private void scanTexts(JCRNodeWrapper node, boolean translateSubtree, Predicate<JCRNodeWrapper> filter, TranslationData data) {
        analyzeNode(node, data);
        if (translateSubtree) {
            JCRContentUtils.getChildrenOfType(node, SUBTREE_ITERABLE_TYPES)
                    .stream()
                    .filter(Optional.ofNullable(filter).orElse(ALL_NODES_PREDICATE))
                    .forEach(child -> scanTexts(child, true, filter, data));
        }
    }

    private void analyzeNode(JCRNodeWrapper node, TranslationData data) {
        if (!isTranslatableNode(node)) return;
        if (checkPendingModifications && !hasPendingModifications(node)) return;

        final PropertyIterator properties;
        try {
            properties = node.getProperties();
        } catch (RepositoryException e) {
            logger.error("", e);
            return;
        }
        while (properties.hasNext()) {
            final Property property = properties.nextProperty();
            switch (getPropertyAction(property)) {
                case TRANSLATE:
                    trackProperty(property, node, data::trackText);
                    break;
                case COPY:
                    trackProperty(property, node, data::trackCopiedValue);
                    break;
                case IGNORE:
            }
        }
    }

    private void trackProperty(Property property, JCRNodeWrapper node, BiConsumer<String, String> tracker) {
        try {
            final String key = node.getPath() + SLASH + property.getName();
            final String stringValue = StringUtils.trimToNull(property.getValue().getString());
            if (stringValue != null) tracker.accept(key, stringValue);
        } catch (RepositoryException e) {
            logger.error("", e);
        }
    }

    private DeepLTranslationResponse translateAndSave(TranslationData data, String srcLanguage, String targetLanguage, Locale responseLocale) {
        final Map<String, String> translations;
        try {
            translations = generateTranslations(data, srcLanguage, targetLanguage);
        } catch (IllegalStateException e) {
            logger.error(e.getMessage());
            return new DeepLTranslationResponseImpl(false, getText("translatorNotInitialized", responseLocale));
        }
        final String targetLangLabel = new Locale(targetLanguage).getDisplayLanguage(responseLocale);

        if (MapUtils.isEmpty(translations)) {
            return new DeepLTranslationResponseImpl(false, getText("nothingToTranslate", responseLocale, targetLangLabel));
        }

        try {
            final JCRSessionWrapper session = JCRSessionFactory.getInstance().getCurrentUserSession(Constants.EDIT_WORKSPACE, LanguageCodeConverters.languageCodeToLocale(targetLanguage));
            if (saveTranslations(session, translations)) {
                return new DeepLTranslationResponseImpl(true, getText("translationSuccess", responseLocale, targetLangLabel));
            } else {
                return new DeepLTranslationResponseImpl(false, getText("nothingToTranslate", responseLocale, targetLangLabel));
            }
        } catch (RepositoryException e) {
            logger.error("", e);
            return new DeepLTranslationResponseImpl(false, getText("translationError", responseLocale, targetLangLabel));
        }
    }

    private Map<String, String> generateTranslations(TranslationData data, String srcLanguage, String destLanguage) {
        if (translator == null) {
            throw new IllegalStateException("The translator is not initialized");
        }
        if (!data.hasTextToWrite()) {
            return null;
        }

        final String destDeepLLanguage = targetLanguages.getOrDefault(destLanguage, destLanguage);
        final Map<String, String> texts = data.getTexts();
        final int nbTexts = texts.size();
        final List<String> keys = new ArrayList<>(nbTexts);
        final List<String> srcTexts = new ArrayList<>(nbTexts);
        texts.forEach((k, v) -> {
            keys.add(k);
            srcTexts.add(v);
        });
        final List<TextResult> results;
        try {
            results = translator.translateText(srcTexts, srcLanguage, destDeepLLanguage);
        } catch (DeepLException | InterruptedException e) {
            logger.error("Failed to translate content", e);
            return null;
        }

        final Map<String, String> translations = IntStream.range(0, nbTexts)
                .boxed()
                .collect(Collectors.toMap(keys::get, i -> results.get(i).getText()));
        return data.completeTranslations(translations);
    }

    private static boolean saveTranslations(JCRSessionWrapper session, Map<String, String> translations) {
        final AtomicBoolean hasSavedSomething = new AtomicBoolean(false);
        translations.forEach((path, value) -> {
            try {
                final JCRNodeWrapper node = session.getNode(StringUtils.substringBeforeLast(path, SLASH));
                final String propertyName = StringUtils.substringAfterLast(path, SLASH);
                if (node.hasProperty(propertyName) && StringUtils.equals(node.getPropertyAsString(propertyName), value)) {
                    logger.debug("{} is already translated", path);
                } else {
                    node.setProperty(propertyName, value);
                    hasSavedSomething.set(true);
                }
            } catch (RepositoryException e) {
                logger.error("", e);
            }
        });

        try {
            session.save();
        } catch (RepositoryException e) {
            logger.error("", e);
        }

        return hasSavedSomething.get();
    }

    private boolean isTranslatableNode(JCRNodeWrapper node) {
        try {
            if (node.isNodeType(Constants.JAHIANT_PAGE)) return true;
            if (node.isNodeType(Constants.JAHIANT_CONTENT)) return true;
        } catch (RepositoryException e) {
            logger.error("", e);
        }
        return false;
    }

    private boolean hasPendingModifications(JCRNodeWrapper node) {
        try {
            if (!node.hasI18N(node.getSession().getLocale())) return false;
            final Node translationNode = node.getI18N(node.getSession().getLocale());
            if (!translationNode.hasProperty(Constants.JCR_LASTMODIFIED)) return true;
            if (!translationNode.hasProperty(Constants.LASTPUBLISHED)) return true;
            return translationNode.getProperty(Constants.JCR_LASTMODIFIED).getDate().after(translationNode.getProperty(Constants.LASTPUBLISHED).getDate());
        } catch (RepositoryException e) {
            logger.error("", e);
            return false;
        }
    }

    private PropertyAction getPropertyAction(Property property) {
        final ExtendedPropertyDefinition definition;
        try {
            definition = (ExtendedPropertyDefinition) property.getDefinition();
        } catch (RepositoryException e) {
            logger.error("", e);
            return PropertyAction.IGNORE;
        }

        if (!definition.isInternationalized()
                || definition.isMultiple()
                || definition.isHidden()
                || definition.isProtected()) {
            return PropertyAction.IGNORE;
        }

        if (definition.getRequiredType() == PropertyType.STRING
                && !(definition.getSelector() == SelectorType.CHOICELIST)
                && !(definition.getSelector() == SelectorType.CRON)
                && !(definition.getSelector() == SelectorType.TAG)) {
            return PropertyAction.TRANSLATE;
        }

        if (definition.getRequiredType() == PropertyType.STRING
                && definition.getSelector() == SelectorType.CHOICELIST) {
            return PropertyAction.COPY;
        }

        if (definition.getRequiredType() == PropertyType.WEAKREFERENCE) {
            return PropertyAction.COPY;
        }

        return PropertyAction.IGNORE;
    }

    private String getText(String key, Locale responseLocale, Object... args) {
        final String resourceBundle = BundleUtils.getModule(FrameworkUtil.getBundle(this.getClass())).getResourceBundleName();
        try {
            return Messages.getWithArgs(resourceBundle, "translation.response.message.".concat(key), responseLocale, args);
        } catch (MissingResourceException e) {
            logger.warn(e.getMessage());
            return MISSING_RESOURCE + key + MISSING_RESOURCE;
        }
    }
}
