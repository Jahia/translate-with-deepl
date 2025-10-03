package org.jahia.community.translation.deepl.service.impl;

import com.deepl.api.DeepLClient;
import com.deepl.api.DeepLException;
import com.deepl.api.GlossaryLanguagePair;
import com.deepl.api.MultilingualGlossaryInfo;
import com.deepl.api.TextTranslationOptions;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.jahia.api.Constants;
import org.jahia.community.translation.deepl.DeeplConstants;
import org.jahia.services.content.JCRContentUtils;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRTemplate;
import org.jahia.services.content.decorator.JCRFileNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.jahia.community.translation.deepl.DeeplConstants.DEFAULT_GLOSSARY_NAME;
import static org.jahia.community.translation.deepl.DeeplConstants.NODE_PATH_JAHIA_SETTINGS;
import static org.jahia.community.translation.deepl.DeeplConstants.NT_GLOSSARY_ENTRY;
import static org.jahia.community.translation.deepl.DeeplConstants.NT_MULTILANG_GLOSSARY_ENTRY;
import static org.jahia.community.translation.deepl.DeeplConstants.NT_SIMPLE_GLOSSARY_ENTRY;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_DEFAULT_GLOSSARY;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_ENTRY_LAST_SYNC;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_GLOSSARY_REF;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_SRC_LANG;
import static org.jahia.community.translation.deepl.DeeplConstants.PROP_TARGET_LANG;

public class GlossaryManager {

    private static final Logger logger = LoggerFactory.getLogger(GlossaryManager.class);

    private final boolean isPermanentGlossary;
    private String glossaryID;
    private final Map<String, Set<String>> glossaryLanguages = new HashMap<>();
    private final Map<String, Set<String>> supportedGlossaryLanguages = new HashMap<>();
    private final DeepLClient deepLClient;

    public GlossaryManager(String configuredID, BiConsumer<Consumer<TextTranslationOptions>, Boolean> setTextTranslationOption, DeepLClient deepLClient) {
        isPermanentGlossary = StringUtils.isNotBlank(configuredID);
        this.deepLClient = deepLClient;
        try {
            deepLClient.getGlossaryLanguages().forEach(this::trackSupportedGlossaryLanguagePair);
            if (logger.isDebugEnabled()) {
                supportedGlossaryLanguages.forEach(((src, targets) -> logger.debug("Supported glossary langs: {}=>{}", src, String.join(",", targets))));
            }
        } catch (DeepLException | InterruptedException e) {
            logger.error("", e);
        }
        glossaryID = getOrCreateGlossary(configuredID);
        if (StringUtils.isNotBlank(glossaryID)) {
            setTextTranslationOption.accept(opt -> opt.setGlossaryId(glossaryID), true);
            try {
                deepLClient.getMultilingualGlossary(glossaryID).getDictionaries()
                        .forEach(dict -> trackGlossaryLanguagePair(dict.getSourceLanguageCode(), dict.getTargetLanguageCode()));
            } catch (DeepLException | InterruptedException e) {
                logger.error("", e);
            }
        }
    }

    public TextTranslationOptions getTextTranslationOptions(String srcLanguage, String destLanguage, TextTranslationOptions options, TextTranslationOptions optionsNoGlossary) {
        final Boolean useGlossary = isValidLanguagePair(srcLanguage, destLanguage, glossaryLanguages);
        logger.debug("Translation {}->{} , useGlossary: {}", srcLanguage, destLanguage, useGlossary);
        return useGlossary ? options : optionsNoGlossary;
    }

    private String asGlossaryLang(String lang) {
        return StringUtils.substring(lang, 0, 2).toLowerCase();
    }

    private void trackGlossaryLanguagePair(String sourceLang, String targetLang) {
        trackLanguagePair(sourceLang, targetLang, glossaryLanguages);
    }

    private void trackSupportedGlossaryLanguagePair(GlossaryLanguagePair pair) {
        trackLanguagePair(pair.getSourceLanguage(), pair.getTargetLanguage(), supportedGlossaryLanguages);
    }

    private void trackLanguagePair(String sourceLang, String targetLang, Map<String, Set<String>> map) {
        map.computeIfAbsent(sourceLang.toLowerCase(), l -> new HashSet<>()).add(targetLang.toLowerCase());
    }

    private boolean isValidLanguagePair(String sourceLang, String targetLang, Map<String, Set<String>> mapping) {
        return Optional.ofNullable(mapping.get(asGlossaryLang(sourceLang)))
                .map(langs -> langs.contains(asGlossaryLang(targetLang)))
                .orElse(false);
    }

    public void refreshGlossary() {
        getOrCreateGlossary(glossaryID);
    }

    public void recreateGlossary() {
        glossaryLanguages.clear();
        if (isPermanentGlossary) {
            logger.debug("Emptyting the glossary");
            try {
                deepLClient.getMultilingualGlossary(glossaryID).getDictionaries().forEach(dict -> {
                    try {
                        deepLClient.deleteMultilingualGlossaryDictionary(glossaryID, dict);
                    } catch (DeepLException | InterruptedException e) {
                        logger.error("", e);
                    }
                });
                getOrCreateGlossary(glossaryID, true);
            } catch (DeepLException | InterruptedException e) {
                logger.error("", e);
            }
        } else {
            try {
                logger.debug("Deleting the glossary {}", glossaryID);
                deepLClient.deleteMultilingualGlossary(glossaryID);
                glossaryID = getOrCreateGlossary(null, true);
                logger.debug("Created a new glossary {}", glossaryID);
            } catch (DeepLException | InterruptedException e) {
                logger.error("", e);
            }
        }
    }

    private String getOrCreateGlossary(String id) {
        return getOrCreateGlossary(id, false);
    }

    private String getOrCreateGlossary(String id, boolean ignoreLastSyncDate) {
        try {
            return JCRTemplate.getInstance().doExecuteWithSystemSession(session -> {
                boolean refreshNeeded = true;
                final JCRNodeWrapper deeplSettingsNode;
                final JCRNodeWrapper settingsRoot = session.getNode(NODE_PATH_JAHIA_SETTINGS);
                final List<JCRNodeWrapper> settingsNodes = JCRContentUtils.getChildrenOfType(settingsRoot, DeeplConstants.NT_DEEPL_SETTINGS);
                if (CollectionUtils.isEmpty(settingsNodes)) {
                    deeplSettingsNode = settingsRoot.addNode(DeeplConstants.NODE_NAME_DEEPL_SETTINGS, DeeplConstants.NT_DEEPL_SETTINGS);
                    session.save();
                } else if (settingsNodes.size() > 1) {
                    logger.error("Multiple DEEPL settings nodes found");
                    return null;
                } else {
                    deeplSettingsNode = settingsNodes.iterator().next();
                }

                final String currentGlossary;
                if (StringUtils.isNotBlank(id)) {
                    if (glossaryExists(id)) {
                        currentGlossary = id;
                        logger.debug("Using the glossary defined in the cfg file"); // TODO: what if we delete the ID in the cfg? We will continue with the same ID copied on the settings node, whereas the user might expect to have stopped using it!
                    } else {
                        logger.error("Impossible to use the specified glossary, since it does not exist. Please review your configuration file");
                        return null;
                    }
                } else {
                    if (deeplSettingsNode.hasProperty(PROP_DEFAULT_GLOSSARY)) {
                        final String propGlossaryID = deeplSettingsNode.getPropertyAsString(PROP_DEFAULT_GLOSSARY);
                        if (glossaryExists(propGlossaryID)) {
                            logger.debug("Using the glossary defined on the settings node");
                            currentGlossary = propGlossaryID;
                        } else {
                            logger.error("Impossible to use the specified glossary, since it does not exist. Please review your settings node");
                            // TODO If forceRecreation is checked, we should create a new glossary
                            return null;
                        }
                    } else {
                        currentGlossary = updateGlossaryEntries(null, deeplSettingsNode, ignoreLastSyncDate);
                        if (StringUtils.isBlank(currentGlossary)) {
                            logger.debug("No glossary");
                            return null;
                        }
                        refreshNeeded = false;
                    }
                }

                if (!deeplSettingsNode.hasProperty(PROP_DEFAULT_GLOSSARY) ||
                        !StringUtils.equals(deeplSettingsNode.getPropertyAsString(PROP_DEFAULT_GLOSSARY), currentGlossary)) {
                    deeplSettingsNode.setProperty(PROP_DEFAULT_GLOSSARY, currentGlossary);
                    session.save();
                }

                if (refreshNeeded) {
                    updateGlossaryEntries(currentGlossary, deeplSettingsNode, ignoreLastSyncDate);
                }

                return currentGlossary;
            });
        } catch (RepositoryException e) {
            logger.error("", e);
            return null;
        }
    }

    private boolean glossaryExists(String id) {
        if (StringUtils.isBlank(id)) return false;

        MultilingualGlossaryInfo multilingualGlossary = null;
        try {
            multilingualGlossary = deepLClient.getMultilingualGlossary(id);
        } catch (DeepLException | InterruptedException e) {
            logger.error("", e);
        }
        if (multilingualGlossary == null) {
            logger.error("No glossary found for the ID {}", id);
            return false;
        }
        return true;
    }

    private String updateGlossaryEntries(String glossaryID, JCRNodeWrapper deeplSettingsNode, boolean ignoreLastSyncDate) {
        final List<JCRNodeWrapper> glossaryEntryNodes = JCRContentUtils.getChildrenOfType(deeplSettingsNode, NT_GLOSSARY_ENTRY);
        if (CollectionUtils.isEmpty(glossaryEntryNodes)) {
            logger.debug("No entries uploaded, no need to initialize or update a glossary");
            return glossaryID;
        }

        final AtomicReference<String> id = new AtomicReference<>(glossaryID);
        glossaryEntryNodes.forEach(glossaryEntryNode -> {
            try {
                processEntryNode(glossaryEntryNode, id, ignoreLastSyncDate);
            } catch (RepositoryException | DeepLException | InterruptedException | IOException |
                     GlossaryManagementException e) {
                logger.error("", e);
            } catch (MultiGlossaryManagementException e) {
                e.getWrappedErrors().forEach(w -> logger.error("", w));
            }
        });
        return id.get();
    }

    private void processEntryNode(JCRNodeWrapper glossaryEntryNode, AtomicReference<String> glossaryID, boolean ignoreLastSyncDate) throws DeepLException, GlossaryManagementException, MultiGlossaryManagementException, RepositoryException, IOException, InterruptedException {
        final JCRFileNode glossaryFile = (JCRFileNode) glossaryEntryNode.getProperty(PROP_GLOSSARY_REF).getNode();
        final JCRNodeWrapper contentNode = glossaryFile.getNode(Constants.JCR_CONTENT);
        if (!contentNode.isNodeType(DeeplConstants.NT_GLOSSARY_ENTRY_DATA)) {
            contentNode.addMixin(DeeplConstants.NT_GLOSSARY_ENTRY_DATA);
            contentNode.saveSession();
        }
        if (!ignoreLastSyncDate && glossaryID.get() != null) {
            final Calendar csvLastModifiedDate = Calendar.getInstance();
            csvLastModifiedDate.setTime(glossaryFile.getContentLastModifiedAsDate());
            if (glossaryEntryNode.hasProperty(PROP_ENTRY_LAST_SYNC) && glossaryEntryNode.getProperty(PROP_ENTRY_LAST_SYNC).getDate().after(csvLastModifiedDate)) {
                logger.debug("Skipping {} , as its content has not changed since the last sync", glossaryEntryNode.getCanonicalPath());
                return;
            }
        }
        if (glossaryEntryNode.isNodeType(NT_SIMPLE_GLOSSARY_ENTRY)) {
            processSimpleEntryNode(glossaryEntryNode, glossaryFile, glossaryID);
        } else if (glossaryEntryNode.isNodeType(NT_MULTILANG_GLOSSARY_ENTRY)) {
            processMultiLangEntryNode(glossaryEntryNode, glossaryFile, glossaryID);
        } else {
            logger.error("Unexpected node type: {}", glossaryEntryNode.getCanonicalPath());
            return;
        }

        glossaryEntryNode.setProperty(PROP_ENTRY_LAST_SYNC, Calendar.getInstance());
        glossaryEntryNode.saveSession();
    }

    private void processSimpleEntryNode(JCRNodeWrapper glossaryEntryNode, JCRFileNode glossaryFile, AtomicReference<String> glossaryID) throws DeepLException, InterruptedException, IOException, GlossaryManagementException {
        logger.debug("Processing a simple entry: {}", glossaryEntryNode.getCanonicalPath());
        final String sourceLang = glossaryEntryNode.getPropertyAsString(PROP_SRC_LANG);
        final String targetLang = glossaryEntryNode.getPropertyAsString(PROP_TARGET_LANG);
        pushGlossaryContent(sourceLang, targetLang, getCsvFromSimpleEntryNode(glossaryFile), glossaryID);
    }

    private String getCsvFromSimpleEntryNode(JCRFileNode glossaryFile) throws IOException {
        return IOUtils.toString(glossaryFile.getFileContent().downloadFile(), StandardCharsets.UTF_8);
    }

    private void processMultiLangEntryNode(JCRNodeWrapper glossaryEntryNode, JCRFileNode glossaryFile, AtomicReference<String> glossaryID) throws MultiGlossaryManagementException, IOException {
        logger.debug("Processing a multilang entry: {}", glossaryEntryNode.getCanonicalPath());
        final InputStream inputStream = glossaryFile.getFileContent().downloadFile();
        final InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);

        // TODO check if CSVFormat.EXCEL is of any interest https://commons.apache.org/proper/commons-csv/apidocs/index.html
        final CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .get()
                .parse(reader);
        final List<CSVRecord> records = parser
                .stream()
                .collect(Collectors.toList());
        final List<String> languages = parser.getHeaderNames().stream()
                .filter(l -> {
                    if (supportedGlossaryLanguages.containsKey(l)) return true;
                    logger.error("Skipping column for language {} which is not supported in a glossary", l);
                    return false;
                })
                .collect(Collectors.toList());

        if (languages.size() < 2) {
            logger.error("Unsufficient number of languages: {}", glossaryEntryNode.getCanonicalPath());
            return;
        }

        final AtomicReference<MultiGlossaryManagementException> errors = new AtomicReference<>();
        languages.forEach(srcLang ->
                languages.stream()
                        .distinct()
                        .filter(l2 -> !StringUtils.equals(srcLang, l2))
                        .forEach(targetLang -> {
                            final StringBuilder data = new StringBuilder();
                            try (final CSVPrinter printer = new CSVPrinter(data, CSVFormat.DEFAULT)) {
                                records.forEach(srcLine -> {
                                    try {
                                        printer.printRecord(srcLine.get(srcLang), srcLine.get(targetLang));
                                    } catch (IOException e) {
                                        logger.error("", e);
                                    }
                                });
                                pushGlossaryContent(srcLang, targetLang, data.toString(), glossaryID);
                            } catch (GlossaryManagementException | IOException | DeepLException | InterruptedException e) {
                                if (errors.get() == null) errors.set(new MultiGlossaryManagementException(e));
                                else errors.get().addError(e);
                            }
                        })
        );
        if (errors.get() != null) throw errors.get();
    }

    private void pushGlossaryContent(String sourceLang, String targetLang, String csv, AtomicReference<String> glossaryID) throws DeepLException, InterruptedException, GlossaryManagementException {
        if (StringUtils.isBlank(csv)) {
            throw new GlossaryManagementException(String.format("Trying to push an empty CSV for %s->%s", sourceLang, targetLang));
        }
        if (!isValidLanguagePair(sourceLang, targetLang, supportedGlossaryLanguages)) {
            throw new GlossaryManagementException(String.format("Trying to push glossary content for languages which are not allowed in a glossary: %s->%s", sourceLang, targetLang));
        }
        if (glossaryID.get() == null) {
            final MultilingualGlossaryInfo glossaryInfo = deepLClient.createMultilingualGlossaryFromCsv(DEFAULT_GLOSSARY_NAME, sourceLang, targetLang, csv);
            glossaryID.set(glossaryInfo.getGlossaryId());
            logger.info("Created the glossary with {}->{}", sourceLang, targetLang);
        } else {
            deepLClient.replaceMultilingualGlossaryDictionaryFromCsv(glossaryID.get(), sourceLang, targetLang, csv);
            logger.info("Updating the glossary for {}->{}", sourceLang, targetLang);
        }
        trackGlossaryLanguagePair(sourceLang, targetLang);
    }
}
