//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.appwork.storage.JSonMapperException;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.DebugMode;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.controlling.filter.CompiledFiletypeFilter;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.plugins.components.hls.HlsContainer;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
import jd.config.Property;
import jd.config.SubConfiguration;
import jd.controlling.AccountController;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.Plugin;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class XHamsterCom extends PluginForHost {
    public XHamsterCom(PluginWrapper wrapper) {
        super(wrapper);
        /* Actually only free accounts are supported */
        this.enablePremium("http://xhamsterpremiumpass.com/");
        setConfigElements();
    }

    /** Make sure this is the same in classes XHamsterCom and XHamsterGallery! */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "xhamster.com", "xhamster.xxx", "xhamster.desi", "xhamster.one", "xhamster1.desi", "xhamster2.desi", "xhamster3.desi", "openxh.com", "openxh1.com", "openxh2.com" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            /* Videos current pattern */
            String pattern = "https?://(?:[a-z0-9\\-]+\\.)?" + buildHostsPatternPart(domains) + "/(?:preview|movies|videos)/[a-z0-9\\-]+\\-[A-Za-z0-9\\-]+(?:$|\\?)";
            /* Embed pattern: 2020-05-08: /embed/123 = current pattern, x?embed.php = old one */
            pattern += "|https?://(?:[a-z0-9\\-]+\\.)?" + buildHostsPatternPart(domains) + "/(embed/[A-Za-z0-9]+|x?embed\\.php\\?video=[A-Za-z0-9]+)";
            /* Movies old pattern */
            pattern += "|https?://(?:[a-z0-9\\-]+\\.)?" + buildHostsPatternPart(domains) + "/movies/[0-9]+/[^/]+\\.html";
            /* Premium pattern */
            pattern += "|https?://gold\\.xhamsterpremium\\.com/videos/([A-Za-z0-9]+)";
            ret.add(pattern);
        }
        return ret.toArray(new String[0]);
    }

    public static String buildHostsPatternPart(String[] domains) {
        final StringBuilder pattern = new StringBuilder();
        pattern.append("(?:");
        for (int i = 0; i < domains.length; i++) {
            final String domain = domains[i];
            if (i > 0) {
                pattern.append("|");
            }
            if ("xhamster.com".equals(domain)) {
                pattern.append("xhamster\\d*\\.(?:com|xxx|desi|one)");
            } else {
                pattern.append(Pattern.quote(domain));
            }
        }
        pattern.append(")");
        return pattern.toString();
    }

    /* DEV NOTES */
    /* Porn_plugin */
    public static final long      trust_cookie_age                = 300000l;
    private static final String   ALLOW_MULTIHOST_USAGE           = "ALLOW_MULTIHOST_USAGE";
    private static final boolean  default_allow_multihoster_usage = false;
    private static final String   HTML_PAID_VIDEO                 = "class=\"buy_tips\"|<tipt>This video is paid</tipt>";
    final String                  SELECTED_VIDEO_FORMAT           = "SELECTED_VIDEO_FORMAT";
    /* The list of qualities/formats displayed to the user */
    private static final String[] FORMATS                         = new String[] { "Best available", "240p", "480p", "720p", "960p", "1080p", "1440p", "2160p" };
    private boolean               friendsOnly                     = false;
    public static final String    domain_premium                  = "xhamsterpremium.com";
    public static final String    api_base_premium                = "https://xhamsterpremium.com/api";

    private void setConfigElements() {
        String user_text;
        if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
            user_text = "Erlaube den Download von Links dieses Anbieters über Multihoster (nicht empfohlen)?\r\n<html><b>Kann die Anonymität erhöhen, aber auch die Fehleranfälligkeit!</b>\r\nAktualisiere deine(n) Multihoster Account(s) nach dem Aktivieren dieser Einstellung um diesen Hoster in der Liste der unterstützten Hoster deines/r Multihoster Accounts zu sehen (sofern diese/r ihn unterstützen).</html>";
        } else {
            user_text = "Allow links of this host to be downloaded via multihosters (not recommended)?\r\n<html><b>This might improve anonymity but perhaps also increase error susceptibility!</b>\r\nRefresh your multihoster account(s) after activating this setting to see this host in the list of the supported hosts of your multihost account(s) (in case this host is supported by your used multihost(s)).</html>";
        }
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), ALLOW_MULTIHOST_USAGE, user_text).setDefaultValue(default_allow_multihoster_usage));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_SEPARATOR));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_COMBOBOX_INDEX, getPluginConfig(), SELECTED_VIDEO_FORMAT, FORMATS, "Preferred Format").setDefaultValue(0));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), "Filename_id", "Only for videos: Change Choose file name to 'filename_ID.exe' e.g. 'test_48604.mp4' ?").setDefaultValue(false));
    }

    /* NO OVERRIDE!! We need to stay 0.9*compatible */
    public boolean allowHandle(final DownloadLink downloadLink, final PluginForHost plugin) {
        if (this.getPluginConfig().getBooleanProperty(ALLOW_MULTIHOST_USAGE, default_allow_multihoster_usage)) {
            return true;
        } else {
            return downloadLink.getHost().equalsIgnoreCase(plugin.getHost());
        }
    }

    @Override
    public String getAGBLink() {
        return "http://xhamster.com/terms.php";
    }

    private static final String TYPE_MOBILE    = "(?i).+m\\.xhamster\\.+";
    private static final String TYPE_EMBED     = "(?i)^https?://(?:www\\.)?xhamster\\.[^/]+/(?:x?embed\\.php\\?video=|embed/)([A-Za-z0-9\\-]+)(?:$|\\?)";
    private static final String TYPE_PREMIUM   = ".+xhamsterpremium\\.com.+";
    private static final String NORESUME       = "NORESUME";
    private static Object       ctrlLock       = new Object();
    private final String        recaptchav2    = "<div class=\"text\">In order to watch this video please prove you are a human\\.\\s*<br> Click on checkbox\\.</div>";
    private String              dllink         = null;
    private String              vq             = null;
    private static final String DOMAIN_CURRENT = "xhamster.com";

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        link.setUrlDownload(link.getPluginPatternMatcher().replaceAll("://(www\\.)?([a-z]{2}\\.)?", "://"));
        if (link.getPluginPatternMatcher().matches(TYPE_MOBILE) || link.getPluginPatternMatcher().matches(TYPE_EMBED)) {
            link.setUrlDownload("https://xhamster.com/videos/" + getLinkpart(link));
        } else {
            final String thisdomain = new Regex(link.getPluginPatternMatcher(), "https?://(?:www\\.)?([^/]+)/.+").getMatch(0);
            link.getPluginPatternMatcher().replace(thisdomain, DOMAIN_CURRENT);
        }
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String linkid = getFID(link);
        if (linkid != null) {
            return this.getHost() + "://" + linkid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        if (link.getPluginPatternMatcher() == null) {
            return null;
        } else {
            final String fid;
            if (link.getPluginPatternMatcher().matches(TYPE_PREMIUM)) {
                fid = new Regex(link.getPluginPatternMatcher(), this.getSupportedLinks()).getMatch(0);
            } else if (link.getPluginPatternMatcher().matches(TYPE_EMBED)) {
                fid = new Regex(link.getPluginPatternMatcher(), TYPE_EMBED).getMatch(0);
            } else if (link.getPluginPatternMatcher().matches(TYPE_MOBILE)) {
                fid = new Regex(link.getPluginPatternMatcher(), "https?://[^/]+/[^/]+/[^/]*?([a-z0-9]+)(/|$|\\?)").getMatch(0);
            } else {
                fid = new Regex(link.getPluginPatternMatcher(), "(?:movies|videos)/[^/]*?([a-z0-9]+)(/|$|\\?)").getMatch(0);
            }
            return fid;
        }
    }

    /**
     * Returns string containing url-name AND linkID e.g. xhamster.com/videos/some-name-here-bla-7653421 --> linkpart =
     * 'some-name-here-bla-7653421'
     */
    private String getLinkpart(final DownloadLink dl) {
        String linkpart = null;
        if (dl.getPluginPatternMatcher().matches(TYPE_MOBILE)) {
            linkpart = new Regex(dl.getPluginPatternMatcher(), "https?://[^/]+/[^/]+/(.+)").getMatch(0);
        } else if (!dl.getPluginPatternMatcher().matches(TYPE_EMBED)) {
            linkpart = new Regex(dl.getPluginPatternMatcher(), "videos/([\\w\\-]+\\-[a-z0-9]+)").getMatch(0);
        }
        if (linkpart == null) {
            /* Fallback e.g. for embed URLs */
            linkpart = getFID(dl);
        }
        return linkpart;
    }

    /**
     * JD2 CODE. DO NOT USE OVERRIDE FOR JD=) COMPATIBILITY REASONS!
     */
    public boolean isProxyRotationEnabledForLinkChecker() {
        return false;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, false);
    }

    public AvailableStatus requestFileInformation(final DownloadLink link, final boolean isDownload) throws Exception {
        synchronized (ctrlLock) {
            friendsOnly = false;
            link.setMimeHint(CompiledFiletypeFilter.VideoExtensions.MP4);
            br.setFollowRedirects(true);
            prepBr();
            /* quick fix to force old player */
            String filename = null;
            String filesizeStr = null;
            Account aa = AccountController.getInstance().getValidAccount(this.getHost());
            if (aa == null) {
                aa = AccountController.getInstance().getValidAccount(domain_premium);
            }
            if (aa != null) {
                login(aa, false);
            }
            br.getPage(link.getPluginPatternMatcher());
            if (br.getHttpConnection().getResponseCode() == 423) {
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Conversion of video processing", 60 * 60 * 1000l);
            }
            if (link.getPluginPatternMatcher().matches(TYPE_PREMIUM)) {
                /* Premium content */
                filename = br.getRegex("<div class=\"spoiler__content\">([^<>\"]+)</div>").getMatch(0);
                if (aa == null || aa.getType() != AccountType.PREMIUM) {
                    /* Free / Free-Account users can only download low quality trailers */
                    this.dllink = br.getRegex("<video src=\"(http[^<>\"]+)\"").getMatch(0);
                } else {
                    /* Premium users can download the full videos in different qualities */
                    if (isDownload) {
                        this.dllink = getDllinkPremium(isDownload);
                    } else {
                        filesizeStr = getDllinkPremium(isDownload);
                        if (filesizeStr != null) {
                            link.setDownloadSize(SizeFormatter.getSize(filesizeStr));
                        }
                    }
                }
                if (filename != null) {
                    link.setFinalFileName(filename + ".mp4");
                } else {
                    link.setName(this.getFID(link) + ".mp4");
                }
            } else {
                final int responsecode = br.getRequest().getHttpConnection().getResponseCode();
                if (responsecode == 423) {
                    if (br.containsHTML(">\\s*This (gallery|video) is visible (for|to) <")) {
                        friendsOnly = true;
                        return AvailableStatus.TRUE;
                    } else if (br.containsHTML("Conversion of video processing")) {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Conversion of video processing", 60 * 60 * 1000l);
                    } else if (br.containsHTML("<title>Page was deleted</title>")) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    } else if (isPasswordProtected()) {
                        return AvailableStatus.TRUE;
                    }
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                } else if (responsecode == 404 || responsecode == 410 || responsecode == 452) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                // embeded correction --> Usually not needed
                if (link.getPluginPatternMatcher().matches(".+/xembed\\.php.*")) {
                    logger.info("Trying to change embed URL --> Real URL");
                    String realpage = br.getRegex("main_url=(https?[^\\&]+)").getMatch(0);
                    if (realpage != null) {
                        logger.info("Successfully changed: " + link.getPluginPatternMatcher() + " ----> " + realpage);
                        link.setUrlDownload(Encoding.htmlDecode(realpage));
                        br.getPage(link.getPluginPatternMatcher());
                    } else {
                        logger.info("Failed to change embed URL --> Real URL");
                    }
                }
                // recaptchav2 here, don't trigger captcha until download....
                if (br.containsHTML(recaptchav2)) {
                    if (!isDownload) {
                        return AvailableStatus.UNCHECKABLE;
                    } else {
                        final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                        final Browser captcha = br.cloneBrowser();
                        captcha.getHeaders().put("Accept", "*/*");
                        captcha.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                        captcha.getPage("/captcha?g-recaptcha-response=" + recaptchaV2Response);
                        br.getPage(br.getURL());
                    }
                }
                if (br.containsHTML("(403 Forbidden|>This video was deleted<)")) {
                    throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                }
                final String onlyfor = videoOnlyForFriendsOf();
                if (onlyfor != null) {
                    link.getLinkStatus().setStatusText("Only downloadable for friends of " + onlyfor);
                    link.setName(getFID(link));
                    return AvailableStatus.TRUE;
                } else if (isPasswordProtected()) {
                    return AvailableStatus.TRUE;
                }
                if (link.getFinalFileName() == null || dllink == null) {
                    filename = getFilename(link);
                    if (filename == null) {
                        throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
                    }
                    link.setFinalFileName(filename);
                    if (br.containsHTML(HTML_PAID_VIDEO)) {
                        link.getLinkStatus().setStatusText("To download, you have to buy this video");
                        return AvailableStatus.TRUE;
                    }
                }
            }
            /* 2020-01-31: Do not check filesize if we're currently in download mode as directurl may expire then. */
            if (link.getView().getBytesTotal() <= 0 && !isDownload && dllink != null && !dllink.contains(".m3u8")) {
                final Browser brc = br.cloneBrowser();
                brc.setFollowRedirects(true);
                URLConnectionAdapter con = null;
                try {
                    con = brc.openHeadConnection(dllink);
                    if (looksLikeDownloadableContent(con)) {
                        if (con.getCompleteContentLength() > 0) {
                            link.setDownloadSize(con.getCompleteContentLength());
                        }
                    } else {
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error");
                    }
                } finally {
                    try {
                        con.disconnect();
                    } catch (Throwable e) {
                    }
                }
            }
            return AvailableStatus.TRUE;
        }
    }

    /**
     * @returns: Not null = video is only available for friends of user XXX
     */
    private String videoOnlyForFriendsOf() {
        String friendsname = br.getRegex(">([^<>\"]*?)</a>\\'s friends only</div>").getMatch(0);
        if (StringUtils.isEmpty(friendsname)) {
            /* 2019-06-05 */
            friendsname = br.getRegex("This video is visible to <br>friends of <a href=\"[^\"]+\">([^<>\"]+)</a> only").getMatch(0);
        }
        return friendsname;
    }

    private boolean isPasswordProtected() {
        return br.containsHTML("class=\"video\\-password\\-block\"");
    }

    private String getSiteTitle() {
        final String title = br.getRegex("<title.*?>([^<>\"]*?)\\s*\\-\\s*xHamster(" + buildHostsPatternPart(getPluginDomains().get(0)) + ")?</title>").getMatch(0);
        return title;
    }

    private String getFilename(final DownloadLink link) throws PluginException, IOException {
        final String fid = getFID(link);
        String filename = br.getRegex("\"videoEntity\"\\s*:\\s*\\{[^\\}\\{]*\"title\"\\s*:\\s*\"([^<>\"]*?)\"").getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<h1.*?itemprop=\"name\">(.*?)</h1>").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("\"title\":\"([^<>\"]*?)\"").getMatch(0);
            }
        }
        if (filename == null) {
            filename = getSiteTitle();
        }
        if (filename == null) {
            /* Fallback to URL filename - first try to get nice name from URL. */
            filename = new Regex(br.getURL(), "/(?:videos|movies)/(.+)\\d+(?:$|\\?)").getMatch(0);
            if (filename == null) {
                /* Last chance */
                filename = new Regex(br.getURL(), "https?://[^/]+/(.+)").getMatch(0);
            }
        }
        if (filename == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dllink = getDllink();
        String ext;
        if (!StringUtils.isEmpty(dllink) && dllink.contains(".m3u8")) {
            ext = ".mp4";
        } else if (!StringUtils.isEmpty(dllink)) {
            ext = getFileNameExtensionFromString(dllink, ".mp4");
        } else {
            ext = ".flv";
        }
        if (getPluginConfig().getBooleanProperty("Filename_id", true)) {
            filename += "_" + fid;
        } else {
            filename = fid + "_" + filename;
        }
        if (vq != null) {
            filename = Encoding.htmlDecode(filename.trim() + "_" + vq);
        } else {
            filename = Encoding.htmlDecode(filename.trim());
        }
        filename += ext;
        return filename;
    }

    /**
     * Returns best filesize if isDownload == false, returns best downloadurl if isDownload == true.
     *
     * @throws Exception
     */
    private String getDllinkPremium(final boolean isDownload) throws Exception {
        final String[] htmls = br.getRegex("(<a[^<>]*class=\"list__item[^\"]*\".*?</a>)").getColumn(0);
        int highestQuality = 0;
        String internalVideoID = null;
        String filesizeStr = null;
        for (final String html : htmls) {
            final String qualityIdentifierStr = new Regex(html, "(\\d+)p").getMatch(0);
            final String qualityFilesizeStr = new Regex(html, "\\((\\d+ (MB|GB))\\)").getMatch(0);
            if (qualityIdentifierStr == null || qualityFilesizeStr == null) {
                continue;
            }
            if (internalVideoID == null) {
                /* This id is the same for every quality */
                internalVideoID = new Regex(html, "data\\-el\\-item\\-id=\"(\\d+)\"").getMatch(0);
            }
            final int qualityTmp = Integer.parseInt(qualityIdentifierStr);
            if (qualityTmp > highestQuality) {
                highestQuality = qualityTmp;
                filesizeStr = qualityFilesizeStr;
            }
        }
        if (!isDownload) {
            return filesizeStr;
        }
        if (internalVideoID == null) {
            logger.warning("internalVideoID is null");
        }
        br.getPage(String.format("https://gold.xhamsterpremium.com/api/videos/%s/original-video-config", internalVideoID));
        LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        entries = (LinkedHashMap<String, Object>) entries.get("downloadFormats");
        return (String) entries.get(Integer.toString(highestQuality));
    }

    /**
     * NOTE: They also have .mp4 version of the videos in the html code -> For mobile devices Those are a bit smaller in size
     */
    @SuppressWarnings("deprecation")
    public String getDllink() throws IOException, PluginException {
        final SubConfiguration cfg = getPluginConfig();
        final int selected_format = cfg.getIntegerProperty(SELECTED_VIDEO_FORMAT, 0);
        final List<String> qualities = new ArrayList<String>();
        switch (selected_format) {
        // fallthrough to automatically choose the next best quality
        default:
        case 7:
            qualities.add("2160p");
        case 6:
            qualities.add("1440p");
        case 5:
            qualities.add("1080p");
        case 4:
            qualities.add("960p");
        case 3:
            qualities.add("720p");
        case 2:
            qualities.add("480p");
        case 1:
            qualities.add("240p");
        }
        String hlsMaster = null;
        try {
            final Map<String, Object> json = JSonStorage.restoreFromString(br.getRegex(">\\s*window\\.initials\\s*=\\s*(\\{.*?\\})\\s*;\\s*<").getMatch(0), TypeRef.HASHMAP);
            final List<Map<String, Object>> sources = (List<Map<String, Object>>) JavaScriptEngineFactory.walkJson(json, "xplayerSettings/sources/standard/mp4");
            if (sources != null) {
                for (final String quality : qualities) {
                    for (Map<String, Object> source : sources) {
                        final String qualityTmp = (String) source.get("quality");
                        String url = (String) source.get("url");
                        if (!StringUtils.equalsIgnoreCase(quality, qualityTmp) || StringUtils.isEmpty(url)) {
                            continue;
                        } else if (hlsMaster == null && url.contains(".m3u8")) {
                            hlsMaster = url;
                            continue;
                        }
                        /* We found the quality we were looking for. */
                        url = br.getURL(url).toString();
                        logger.info("Sources:" + quality + "->" + url);
                        return url;
                    }
                }
            }
        } catch (JSonMapperException e) {
            logger.log(e);
        }
        if (hlsMaster != null) {
            /* 2021-02-01 */
            logger.info("Fallback to HLS download -> " + hlsMaster);
            return hlsMaster;
        }
        final String newPlayer = Encoding.htmlDecode(br.getRegex("videoUrls\":\"(\\{.*?\\]\\})").getMatch(0));
        if (newPlayer != null) {
            // new player
            final Map<String, Object> map = JSonStorage.restoreFromString(JSonStorage.restoreFromString("\"" + newPlayer + "\"", TypeRef.STRING), TypeRef.HASHMAP);
            if (map != null) {
                for (final String quality : qualities) {
                    final Object list = map.get(quality);
                    if (list != null && list instanceof List) {
                        final List<String> urls = (List<String>) list;
                        if (urls.size() > 0) {
                            vq = quality;
                            logger.info("videoUrls:" + quality + "->" + quality);
                            return urls.get(0);
                        }
                    }
                }
            }
        }
        for (final String quality : qualities) {
            // old player
            final String urls[] = br.getRegex(quality + "\"\\s*:\\s*(\"https?:[^\"]+\")").getColumn(0);
            if (urls != null && urls.length > 0) {
                for (String url : urls) {
                    url = JSonStorage.restoreFromString(url, TypeRef.STRING);
                    if (StringUtils.containsIgnoreCase(url, ".mp4")) {
                        final boolean verified = verifyURL(url);
                        if (verified) {
                            vq = quality;
                            logger.info("oldPlayer:" + quality + "->" + quality);
                            return url;
                        }
                    }
                }
            }
        }
        for (final String quality : qualities) {
            // 3d videos
            final String urls[] = br.getRegex(quality + "\"\\s*,\\s*\"url\"\\s*:\\s*(\"https?:[^\"]+\")").getColumn(0);
            if (urls != null && urls.length > 0) {
                String best = null;
                for (String url : urls) {
                    url = JSonStorage.restoreFromString(url, TypeRef.STRING);
                    if (best == null || StringUtils.containsIgnoreCase(url, ".mp4")) {
                        best = url;
                    }
                }
                if (best != null) {
                    vq = quality;
                    logger.info("old3D" + quality + "->" + quality);
                    return best;
                }
            }
        }
        // is the rest still in use/required?
        String ret = null;
        logger.info("Video quality selection failed.");
        int urlmodeint = 0;
        final String urlmode = br.getRegex("url_mode=(\\d+)").getMatch(0);
        if (urlmode != null) {
            urlmodeint = Integer.parseInt(urlmode);
        }
        if (urlmodeint == 1) {
            /* Example-ID: 1815274, 1980180 */
            final Regex secondway = br.getRegex("\\&srv=(https?[A-Za-z0-9%\\.]+\\.xhcdn\\.com)\\&file=([^<>\"]*?)\\&");
            String server = br.getRegex("\\'srv\\'\\s*:\\s*\\'(.*?)\\'").getMatch(0);
            if (server == null) {
                server = secondway.getMatch(0);
            }
            String file = br.getRegex("\\'file\\'\\s*:\\s*\\'(.*?)\\'").getMatch(0);
            if (file == null) {
                file = secondway.getMatch(1);
            }
            if (server == null || file == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            if (file.startsWith("http")) {
                // Examplelink (ID): 968106
                ret = file;
            } else {
                // Examplelink (ID): 986043
                ret = server + "/key=" + file;
            }
            logger.info("urlmode:" + urlmodeint + "->" + ret);
        } else {
            /* E.g. url_mode == 3 */
            /* Example-ID: 685813 */
            String flashvars = br.getRegex("flashvars\\s*:\\s*\"([^<>\"]*?)\"").getMatch(0);
            ret = br.getRegex("\"(https?://\\d+\\.xhcdn\\.com/key=[^<>\"]*?)\" class=\"mp4Thumb\"").getMatch(0);
            if (ret == null) {
                ret = br.getRegex("\"(https?://\\d+\\.xhcdn\\.com/key=[^<>\"]*?)\"").getMatch(0);
            }
            if (ret == null) {
                ret = br.getRegex("\"(https?://\\d+\\.xhcdn\\.com/key=[^<>\"]*?)\"").getMatch(0);
            }
            if (ret == null) {
                ret = br.getRegex("flashvars.*?file=(https?%3.*?)&").getMatch(0);
            }
            if (ret == null && flashvars != null) {
                /* E.g. 4753816 */
                flashvars = Encoding.htmlDecode(flashvars);
                flashvars = flashvars.replace("\\", "");
                final String[] qualities2 = { "1080p", "720p", "480p", "360p", "240p" };
                for (final String quality : qualities2) {
                    ret = new Regex(flashvars, "\"" + quality + "\"\\s*:\\s*\\[\"(https?[^<>\"]*?)\"\\]").getMatch(0);
                    if (ret != null) {
                        logger.info("urlmode:" + urlmodeint + "|quality:" + quality + "->" + ret);
                        break;
                    }
                }
            }
        }
        if (ret == null) {
            // urlmode fails, eg: 1099006
            ret = br.getRegex("video\\s*:\\s*\\{[^\\}]+file\\s*:\\s*('|\")(.*?)\\1").getMatch(1);
            if (ret == null) {
                ret = PluginJSonUtils.getJson(br, "fallback");
                ret = ret.replace("\\", "");
                logger.info("urlmode(fallback):" + urlmodeint + "->" + ret);
            }
        }
        if (ret == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        } else {
            if (ret.contains("&amp;")) {
                ret = Encoding.htmlDecode(ret);
            }
            return ret;
        }
    }

    public boolean verifyURL(String url) throws IOException, PluginException {
        URLConnectionAdapter con = null;
        final Browser br2 = br.cloneBrowser();
        br2.setFollowRedirects(true);
        try {
            con = br2.openHeadConnection(url);
            if (looksLikeDownloadableContent(con)) {
                return true;
            } else {
                throw new IOException();
            }
        } catch (final IOException e) {
            logger.log(e);
            return false;
        } finally {
            try {
                con.disconnect();
            } catch (final Exception e) {
            }
        }
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link, true);
        doFree(link);
    }

    @SuppressWarnings("deprecation")
    public void doFree(final DownloadLink link) throws Exception {
        String passCode = null;
        if (!link.getPluginPatternMatcher().matches(TYPE_PREMIUM)) {
            if (friendsOnly) {
                throw new AccountRequiredException("You need to be friends with uploader");
            }
            // Access the page again to get a new direct link because by checking the availability the first linkisn't valid anymore
            passCode = link.getStringProperty("pass", null);
            br.getPage(link.getPluginPatternMatcher());
            final String onlyfor = videoOnlyForFriendsOf();
            if (onlyfor != null) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            } else if (isPasswordProtected()) {
                final boolean passwordHandlingBroken = true;
                if (passwordHandlingBroken) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, "Password-protected handling broken svn.jdownloader.org/issues/88690");
                }
                if (passCode == null) {
                    passCode = Plugin.getUserInput("Password?", link);
                }
                if (DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
                    /* New way */
                    final String videoID = this.getFID(link);
                    if (videoID == null) {
                        /* This should never happen */
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    /* 2020-09-03: Browser sends crypted password but uncrypted password seems to work fine too */
                    final String json = String.format("[{\"name\":\"entityUnlockModelSync\",\"requestData\":{\"model\":{\"id\":null,\"$id\":\"c280e6b4-d696-479c-bb7d-eb0627d36fb1\",\"modelName\":\"entityUnlockModel\",\"itemState\":\"changed\",\"password\":\"%s\",\"entityModel\":\"videoModel\",\"entityID\":%s}}}]", passCode, videoID);
                    br.getHeaders().put("x-requested-with", "XMLHttpRequest");
                    br.getHeaders().put("content-type", "text/plain");
                    br.getHeaders().put("accept", "*/*");
                    br.postPageRaw("/x-api", json);
                    /*
                     * 2020-09-03: E.g. wrong password:
                     * [{"name":"entityUnlockModelSync","extras":{"result":false,"error":{"password":"Falsches Passwort"}},"responseData":{
                     * "$id":"c280e6b4-d696-479c-bb7d-eb0627d36fb1"}}]
                     */
                    if (br.containsHTML("\"password\"")) {
                        throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
                    }
                    /*
                     * 2020-09-03: WTF:
                     * [{"name":"entityUnlockModelSync","extras":{"result":false,"showCaptcha":true,"code":"403 Forbidden"},"responseData":{
                     * "$id":"c280e6b4-d696-479c-bb7d-eb0627d36fb1"}}]
                     */
                } else {
                    /* Old way */
                    br.postPage(br.getURL(), "password=" + Encoding.urlEncode(passCode));
                    if (isPasswordProtected()) {
                        link.setProperty("pass", Property.NULL);
                        throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
                    }
                }
                link.setFinalFileName(getFilename(link));
            } else if (br.containsHTML(HTML_PAID_VIDEO)) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_ONLY);
            }
            this.dllink = getDllink();
            if (this.dllink == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        }
        if (this.dllink.contains(".m3u8")) {
            /* 2021-02-01: HLS download */
            br.getPage(this.dllink);
            final HlsContainer hlsbest = HlsContainer.findBestVideoByBandwidth(HlsContainer.getHlsQualities(this.br));
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, hlsbest.getDownloadurl());
            dl.startDownload();
        } else {
            boolean resume = true;
            if (link.getBooleanProperty(NORESUME, false)) {
                resume = false;
            }
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, this.dllink, resume, 0);
            if (!looksLikeDownloadableContent(dl.getConnection())) {
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                if (dl.getConnection().getResponseCode() == 416) {
                    logger.info("Response code 416 --> Handling it");
                    if (link.getBooleanProperty(NORESUME, false)) {
                        link.setProperty(NORESUME, Boolean.valueOf(false));
                        throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error 416", 30 * 60 * 1000l);
                    }
                    link.setProperty(NORESUME, Boolean.valueOf(true));
                    link.setChunksProgress(null);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "Server error 416");
                }
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown error");
            }
            if (passCode != null) {
                link.setProperty("pass", passCode);
            }
            dl.startDownload();
        }
    }

    public void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            // used in finally to restore browser redirect status.
            final boolean frd = br.isFollowingRedirects();
            try {
                br.setCookiesExclusive(true);
                prepBr();
                br.setFollowRedirects(true);
                /*
                 * 2020-01-31: They got their free page xhamster.com and paid xhamsterpremium.com. This plugin will always try to login into
                 * both. Free users can also login to xhamsterpremium.to they just cannot watch anything. Failures of premium login will be
                 * ignored and account will be accepted as free account then.
                 */
                final Cookies cookies = account.loadCookies("");
                Cookies premiumCookies = account.loadCookies("premium");
                boolean isloggedinPremium = false;
                if (cookies != null) {
                    logger.info("Trying cookie login");
                    br.setCookies(this.getHost(), cookies, true);
                    if (System.currentTimeMillis() - account.getCookiesTimeStamp("") <= trust_cookie_age && !force) {
                        /* We trust these cookies --> Do not check them */
                        if (premiumCookies != null) {
                            logger.info("Found stored premium cookies");
                            br.setCookies(domain_premium, premiumCookies);
                        }
                        logger.info("Trust cookies without login");
                        return;
                    } else {
                        /* Try to avoid login cookie whenever possible! */
                        br.getPage("https://" + this.getHost() + "/");
                        if (isLoggedInHTML(br)) {
                            /* Save new cookie timestamp */
                            account.saveCookies(br.getCookies(br.getHost()), "");
                            logger.info("Free cookie login successful -> Checking premium cookies");
                            if (premiumCookies != null) {
                                /* Cookies have already been set in lines above */
                                logger.info("Checking premium cookies");
                                br.setCookies(domain_premium, premiumCookies);
                                if (this.checkPremiumLogin()) {
                                    /* Save new premium cookies if they were valid */
                                    account.saveCookies(br.getCookies(br.getHost()), "premium");
                                }
                            }
                            return;
                        } else {
                            /* Reset Browser */
                            logger.info("Free cookie login failed");
                            br.clearCookies(null);
                        }
                    }
                }
                /** 2021-01-08: Free login is broken and premium works for both -> Use premium login only */
                final boolean usePremiumLoginONLY = true;
                if (br.getHost() == null) {
                    br.getPage("https://" + account.getHoster() + "/");
                }
                if (usePremiumLoginONLY) {
                    isloggedinPremium = this.loginPremium(account, true);
                    premiumCookies = br.getCookies(br.getURL());
                    br.getPage(api_base_premium + "/auth/endpoints");
                    String xhamsterComLoginURL = PluginJSonUtils.getJson(this.br, "https://xhamster.com/premium/in");
                    if (StringUtils.isEmpty(xhamsterComLoginURL)) {
                        /* Fallback */
                        xhamsterComLoginURL = br.getRegex("(https?://xhamster\\.com/premium/in\\?[^<>\"]+)").getMatch(0);
                    }
                    if (StringUtils.isEmpty(xhamsterComLoginURL)) {
                        logger.warning("Premium login successful but free login failed");
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                    /* Now we should also be logged in as free- user! */
                    br.getPage(xhamsterComLoginURL);
                } else {
                    if (htmlIsOldDesign(br)) {
                        final Form login = br.getFormbyProperty("name", "loginForm");
                        if (login == null) {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                        /* set action, website changes action in js! */
                        login.setAction(br.getURL("/ajax/login.php").toExternalForm());
                        Browser br = this.br.cloneBrowser();
                        final long now = System.currentTimeMillis();
                        final String xsid;
                        {
                            final ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
                            final ScriptEngine engine = manager.getEngineByName("javascript");
                            engine.eval("res1 = Math.floor(Math.random()*100000000).toString(16);");
                            engine.eval("now = " + now);
                            engine.eval("res2 = now.toString(16).substring(0,8);");
                            xsid = (String) engine.get("res1") + ":" + (String) engine.get("res2");
                        }
                        // set in login form and cookie to the correct section
                        login.put("stats", Encoding.urlEncode(xsid));
                        br.setCookie(br.getHost(), "xsid", xsid);
                        // now some other fingerprint set via js, again cookie and login form
                        final String fingerprint = JDHash.getMD5(System.getProperty("user.timezone") + System.getProperty("os.name"));
                        br.setCookie(br.getHost(), "fingerprint", fingerprint);
                        login.put("fingerprint", fingerprint);
                        login.put("username", Encoding.urlEncode(account.getUser()));
                        login.put("password", Encoding.urlEncode(account.getPass()));
                        login.put("remember", "on");
                        // login.put("_", now + "");
                        br.getHeaders().put("Accept", "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01");
                        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                        br.submitForm(login);
                        /* Account is fine but we need a stupid login captcha */
                        if (br.containsHTML("\"errors\":\"invalid_captcha\"") && br.containsHTML("\\$\\('#loginCaptchaRow'\\)\\.show\\(\\)")) {
                            if (this.getDownloadLink() == null) {
                                final DownloadLink dummyLink = new DownloadLink(this, "Account", "xhamster.com", "http://xhamster.com", true);
                                this.setDownloadLink(dummyLink);
                            }
                            final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                            br = this.br.cloneBrowser();
                            login.put("_", System.currentTimeMillis() + "");
                            br.getHeaders().put("Accept", "text/javascript, application/javascript, application/ecmascript, application/x-ecmascript, */*; q=0.01");
                            br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                            login.put("g-recaptcha-response", Encoding.urlEncode(recaptchaV2Response));
                            br.submitForm(login);
                        }
                    } else {
                        /* 2020-08-31: Current key should be: 6Le0H9IUAAAAAIQylhldG3_JgdRkQInX5RUDXzqG */
                        final String siteKeyV3 = PluginJSonUtils.getJson(br, "recaptchaKeyV3");
                        final String siteKey = PluginJSonUtils.getJson(br, "recaptchaKey");
                        final String id = createID();
                        final String requestdataFormat = "[{\"name\":\"authorizedUserModelSync\",\"requestData\":{\"model\":{\"id\":null,\"$id\":\"%s\",\"modelName\":\"authorizedUserModel\",\"itemState\":\"unchanged\"},\"trusted\":true,\"username\":\"%s\",\"password\":\"%s\",\"remember\":1,\"redirectURL\":null,\"captcha\":\"\",\"g-recaptcha-response\":\"%s\"}}]";
                        final String requestdataFormatCaptcha = "[{\"name\":\"authorizedUserModelSync\",\"requestData\":{\"model\":{\"id\":null,\"$id\":\"%s\",\"modelName\":\"authorizedUserModel\",\"itemState\":\"unchanged\"},\"username\":\"%s\",\"password\":\"%s\",\"remember\":1,\"redirectURL\":null,\"captcha\":\"\",\"trusted\":true,\"g-recaptcha-response\":\"%s\"}}]";
                        String requestData = String.format(requestdataFormat, id, account.getUser(), account.getPass(), "");
                        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                        br.postPageRaw("/x-api", requestData);
                        if (br.containsHTML("showCaptcha\":true")) {
                            if (this.getDownloadLink() == null) {
                                final DownloadLink dummyLink = new DownloadLink(this, "Account", "xhamster.com", "https://xhamster.com", true);
                                this.setDownloadLink(dummyLink);
                            }
                            final String recaptchaV2Response;
                            if (!StringUtils.isEmpty(siteKeyV3)) {
                                /* 2020-03-17 */
                                recaptchaV2Response = getCaptchaHelperHostPluginRecaptchaV2Invisible(this, br, siteKeyV3).getToken();
                            } else {
                                /* Old */
                                recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br, siteKey).getToken();
                            }
                            requestData = String.format(requestdataFormatCaptcha, id, account.getUser(), account.getPass(), recaptchaV2Response);
                            /* TODO: Fix this */
                            br.postPageRaw("/x-api", requestData);
                        }
                    }
                }
                /* Check whether or not we're logged in */
                if (!isLoggedinFree()) {
                    logger.info("Free login failed!");
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                account.saveCookies(br.getCookies(br.getHost()), "");
                logger.info("Checking premium login state");
                if (!isloggedinPremium) {
                    logger.info("Performing full premium login");
                    isloggedinPremium = this.loginPremium(account, false);
                    premiumCookies = br.getCookies(br.getURL());
                }
                if (isloggedinPremium) {
                    /* Only save cookies if login was successful */
                    account.saveCookies(premiumCookies, "premium");
                }
            } catch (final PluginException e) {
                if (e.getLinkStatus() == LinkStatus.ERROR_PREMIUM) {
                    account.clearCookies("");
                }
                throw e;
            } finally {
                br.setFollowRedirects(frd);
            }
        }
    }

    private boolean isLoggedinFree() {
        return br.getCookie(br.getHost(), "UID", Cookies.NOTDELETEDPATTERN) != null && br.getCookie(br.getHost(), "_id", Cookies.NOTDELETEDPATTERN) != null;
    }

    private boolean checkPremiumLogin() throws IOException {
        br.getPage(api_base_premium + "/subscription/get");
        if (br.getHttpConnection().getContentType().contains("json")) {
            logger.info("Successfully checked premium cookies");
            return true;
        } else {
            logger.info("Premium cookies seem to be invalid");
            return false;
        }
    }

    private boolean loginPremium(final Account account, final boolean exceptionOnFailure) throws IOException, PluginException, InterruptedException {
        logger.info("Performing full premium login");
        br.getHeaders().put("Referer", null);
        /* Login premium --> Same logindata */
        br.getPage("https://xhamsterpremium.com/");
        if (this.getDownloadLink() == null) {
            final DownloadLink dummyLink = new DownloadLink(this, "Account", "xhamsterpremium.com", "http://xhamsterpremium.com", true);
            this.setDownloadLink(dummyLink);
        }
        String rcKey = br.getRegex("data-site-key=\"([^\"]+)\"").getMatch(0);
        if (rcKey == null) {
            /* Fallback: reCaptchaKey timestamp: 2020-08-04 */
            rcKey = "6LfoawAVAAAAADDXDc7xDBOkr1FQqdfUrEH5Z7up";
        }
        final String recaptchaV2Response = getCaptchaHelperHostPluginRecaptchaV2Invisible(this, this.br, rcKey).getToken();
        final String csrftoken = br.getRegex("data-name=\"csrf-token\" content=\"([^<>\"]+)\"").getMatch(0);
        if (csrftoken != null) {
            br.getHeaders().put("x-csrf-token", csrftoken);
        } else {
            logger.warning("Failed to find csrftoken --> Premium login might fail because of this");
        }
        br.postPageRaw("/api/auth/signin", String.format("{\"login\":\"%s\",\"password\":\"%s\",\"rememberMe\":\"1\",\"trackingParamsBag\":\"W10=\",\"g-recaptcha-response\":\"%s\",\"recaptcha\":\"%s\"}", account.getUser(), account.getPass(), recaptchaV2Response, recaptchaV2Response));
        final String userId = PluginJSonUtils.getJson(br, "userId");
        final String success = PluginJSonUtils.getJson(br, "success");
        if ("true".equalsIgnoreCase(success) && !StringUtils.isEmpty(userId)) {
            logger.info("Premium login successful");
            return true;
        } else {
            logger.info("Premium login failed");
            if (exceptionOnFailure) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                return false;
            }
        }
    }

    protected CaptchaHelperHostPluginRecaptchaV2 getCaptchaHelperHostPluginRecaptchaV2Invisible(PluginForHost plugin, Browser br, final String key) throws PluginException {
        return new CaptchaHelperHostPluginRecaptchaV2(this, br, key) {
            @Override
            public org.jdownloader.captcha.v2.challenge.recaptcha.v2.AbstractRecaptchaV2.TYPE getType() {
                return TYPE.INVISIBLE;
            }
        };
    }

    private String createID() {
        StringBuffer result = new StringBuffer();
        byte bytes[] = new byte[16];
        SecureRandom random = new SecureRandom();
        random.nextBytes(bytes);
        if (bytes[6] == 15) {
            bytes[6] |= 64;
        }
        if (bytes[8] == 63) {
            bytes[8] |= 128;
        }
        for (int i = 0; i < bytes.length; i++) {
            result.append(String.format("%02x", bytes[i] & 0xFF));
            if (i == 3 || i == 5 || i == 7 || i == 9) {
                result.append("-");
            }
        }
        return result.toString();
    }

    private boolean htmlIsOldDesign(final Browser br) {
        return br.containsHTML("class=\"design\\-switcher\"");
    }

    private boolean isLoggedInHTML(final Browser br) {
        if (htmlIsOldDesign(br)) {
            return br.containsHTML("id=\"menuLogin\"");
        } else {
            return br.containsHTML("\"myProfile\"");
        }
    }

    /** THIS DOES NOT WORK Checks login state for xhamsterpremium.com */
    // private boolean isLoggedInHTMLPremium(final Browser br) {
    // return br.containsHTML("class=\"header__user-title\"");
    // }
    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        ai.setUnlimitedTraffic();
        /* Now check whether this is a free- or a premium account. */
        if (br.getURL() == null || !br.getURL().contains("/subscription/get")) {
            br.getPage(api_base_premium + "/subscription/get");
        }
        /*
         * E.g. error 400 for free users:
         * {"errors":{"_global":["Payment system temporary unavailable. Please try later."]},"userId":1234567}
         */
        long expire = 0;
        final String expireStr = PluginJSonUtils.getJson(br, "expiredAt");
        final String isTrial = PluginJSonUtils.getJson(br, "isTrial");
        if (!StringUtils.isEmpty(expireStr)) {
            expire = TimeFormatter.getMilliSeconds(expireStr, "yyyy-MM-dd HH:mm:ss", Locale.ENGLISH);
        }
        if (expire < System.currentTimeMillis()) {
            ai.setStatus("Free Account");
            account.setType(AccountType.FREE);
        } else {
            String status = "Premium Account";
            if ("true".equalsIgnoreCase(isTrial)) {
                status += " [Trial]";
            }
            ai.setStatus(status);
            ai.setValidUntil(expire, br);
            account.setType(AccountType.PREMIUM);
        }
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link, true);
        /* No need to login as we'll already be loggedin via requestFileInformation. */
        // login(account, false);
        doFree(link);
    }

    protected void prepBr() {
        for (String host : new String[] { "xhamster.com", "xhamster.xxx", "xhamster.desi", "xhamster.one", "xhamster1.desi", "xhamster2.desi" }) {
            br.setCookie(host, "lang", "en");
            br.setCookie(host, "playerVer", "old");
        }
        br.setAllowedResponseCodes(new int[] { 400, 410, 423, 452 });
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}