package org.jahia.community.translation.deepl;

import org.jahia.api.Constants;

public class DeeplConstants {

    public static final String SERVICE_CONFIG_FILE_NAME = "org.jahia.community.translatewithdeepl";
    public static final String SERVICE_CONFIG_FILE_FULLNAME = SERVICE_CONFIG_FILE_NAME + ".cfg";
    public static final String PROP_API_KEY = "translation.deepl.api.key";
    public static final String PROP_DO_NOT_CONSIDER_PUBLICATION_STATUS = "translation.deepl.doNotConsiderPublicationStatus";
    public static final String PROP_USE_HTML_TAG_HANDLING = "translation.deepl.textTranslationOptions.useHtmlTagHandling";
    public static final String PROP_PREFIX_TARGET_LANGUAGES = "targetLanguages.";
    public static final String PROP_GLOSSARY_ID = "translation.deepl.textTranslationOptions.glossaryID";

    public static final String SUBTREE_ITERABLE_TYPES = Constants.JAHIANT_PAGE + "," + Constants.JAHIANT_CONTENT;
    public static final String PROP_ALL_LANGUAGES = "allLanguages";
    public static final String PROP_SRC_LANGUAGE = "srcLanguage";
    public static final String PROP_DEST_LANGUAGE = "destLanguage";
    public static final String PROP_SUB_TREE = "subTree";

    public static final String TRANSLATE_PERMISSION = "deeplTranslate";
    public static final String NODE_PATH_JAHIA_SETTINGS = "/settings";
    public static final String NT_DEEPL_SETTINGS = "dplnt:settings";
    public static final String NODE_NAME_DEEPL_SETTINGS = "deepl-settings";
    public static final String PROP_DEFAULT_GLOSSARY = "defaultGlossary";
    public static final String PROP_FORCE_RECREATION = "forceRecreation";
    public static final String NT_GLOSSARY_ENTRY = "dplmix:glossaryEntry";
    public static final String PROP_GLOSSARY_REF = "glossary";
    public static final String PROP_ENTRY_LAST_SYNC = "lastSync";
    public static final String NT_SIMPLE_GLOSSARY_ENTRY = "dplnt:simpleGlossaryEntry";
    public static final String PROP_SRC_LANG = "sourceLang";
    public static final String PROP_TARGET_LANG = "targetLang";
    public static final String NT_MULTILANG_GLOSSARY_ENTRY = "dplnt:multiLangGlossaryEntry";
    public static final String NT_GLOSSARY_ENTRY_DATA = "dplmix:glossaryEntryData";
    public static final String DEFAULT_GLOSSARY_NAME = "Jahia glossary";

    private DeeplConstants() {

    }

}
