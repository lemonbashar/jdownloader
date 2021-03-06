//jDownloader - Downloadmanager
//Copyright (C) 2012  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.jdownloader.captcha.v2.challenge.recaptcha.v2.CaptchaHelperHostPluginRecaptchaV2;
import org.jdownloader.downloader.hls.HLSDownloader;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.ConfigContainer;
import jd.config.ConfigEntry;
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
import jd.plugins.AccountInvalidException;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "video.fc2.com" }, urls = { "https?://(?:video\\.fc2\\.com|xiaojiadianvideo\\.asia|jinniumovie\\.be)/((?:[a-z]{2}/)?(?:a/)?flv2\\.swf\\?i=|(?:[a-z]{2}/)?(?:a/)?content/)\\w+" })
public class VideoFCTwoCom extends PluginForHost {
    public VideoFCTwoCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("http://fc2.com");
        setConfigElements();
    }

    @Override
    public String[] siteSupportedNames() {
        return new String[] { "video.fc2.com", "xiaojiadianvideo.asia", "jinniumovie.be" };
    }

    private String              httpDownloadurl              = null;
    private String              hlsMaster                    = null;
    private String              trailerURL                   = null;
    private boolean             server_issues                = false;
    private static final String fastLinkCheck                = "fastLinkCheck";
    private final boolean       fastLinkCheck_default        = true;
    private static final String allowTrailerDownload         = "allowTrailerDownload";
    private final boolean       allowTrailerDownload_default = false;
    private static final String PROPERTY_PREMIUMONLY         = "PREMIUMONLY";

    private void setConfigElements() {
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), fastLinkCheck, "Enable fast linkcheck, doesn't perform filesize checks! Filesize will be updated when download starts.").setDefaultValue(fastLinkCheck_default));
        getConfig().addEntry(new ConfigEntry(ConfigContainer.TYPE_CHECKBOX, getPluginConfig(), allowTrailerDownload, "Download trailer if full video is not available?").setDefaultValue(allowTrailerDownload_default));
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
        return new Regex(link.getPluginPatternMatcher(), "(?:i=|content/)(.+)").getMatch(0);
    }

    private Browser prepareBrowser(final Browser prepBr) {
        prepBr.getHeaders().put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0.4280.88 Safari/537.36");
        prepBr.setCustomCharset("utf-8");
        return prepBr;
    }

    @Override
    public String getAGBLink() {
        return "http://help.fc2.com/common/tos/en/";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 4;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return 4;
    }

    @SuppressWarnings("deprecation")
    public void correctDownloadLink(final DownloadLink link) {
        final boolean subContent = new Regex(link.getDownloadURL(), "/a/content/").matches();
        link.setUrlDownload("https://video.fc2.com/en/" + (subContent ? "a/content/" : "content/") + new Regex(link.getDownloadURL(), "([A-Za-z0-9]+)/?$").getMatch(0));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        return requestFileInformation(link, null);
    }

    private AvailableStatus requestFileInformation(final DownloadLink link, Account account) throws Exception {
        final String fid = getFID(link);
        /* Prefer pre-given account but fallback to *any* valid account. */
        if (account == null) {
            account = AccountController.getInstance().getValidAccount(this.getHost());
        }
        if (account != null) {
            this.login(account, true, link.getPluginPatternMatcher());
        } else {
            prepareBrowser(br);
            br.setFollowRedirects(true);
            br.getPage(link.getPluginPatternMatcher());
        }
        if (isOffline(fid)) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String newAPIVideotoken = br.getRegex("\\'ae\\'\\s*?,\\s*?\\'([a-f0-9]{32})\\'").getMatch(0);
        if (newAPIVideotoken == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        String filename = null;
        String uploadername = null;
        /**
         * 2019-01-28: Some videos are still based on their old (flash-)player and cannot be checked via their new API! </br>
         * 2020-12-18: TODO: re-check this statement - new API should be used for all videos by now!
         */
        // final boolean useNewAPI = account == null && newAPIVideotoken != null;
        String filenamePrefix = "";
        Map<String, Object> entries;
        br.getHeaders().put("X-FC2-Video-Access-Token", newAPIVideotoken);
        br.getPage("https://" + this.getHost() + "/api/v3/videoplayer/" + fid + "?" + newAPIVideotoken + "=1&tk=&fs=0");
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        entries = JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        filename = (String) entries.get("title");
        uploadername = (String) JavaScriptEngineFactory.walkJson(entries, "owner/name");
        if (StringUtils.isEmpty(filename)) {
            /* Fallback */
            filename = fid;
        }
        br.getPage("/api/v3/videoplaylist/" + fid + "?sh=1&fs=0");
        entries = JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        final Map<String, Object> playlist = (Map<String, Object>) entries.get("playlist");
        /* HTTP Streams */
        final String[] qualitiesInBestOrder = new String[] { "hq", "nq", "lq" };
        for (final String possibleQuality : qualitiesInBestOrder) {
            this.httpDownloadurl = (String) playlist.get(possibleQuality);
            if (!StringUtils.isEmpty(this.httpDownloadurl)) {
                break;
            }
        }
        /* HLS streams */
        this.hlsMaster = (String) playlist.get("master");
        /* Trailer -> Also http stream */
        this.trailerURL = (String) playlist.get("sample");
        if (StringUtils.isEmpty(this.httpDownloadurl) && StringUtils.isEmpty(this.hlsMaster) && !StringUtils.isEmpty(this.trailerURL) && this.getPluginConfig().getBooleanProperty(allowTrailerDownload, allowTrailerDownload_default)) {
            logger.info("Trailer download is allowed and trailer is available");
            /* Trailers are always available as http streams */
            this.httpDownloadurl = this.trailerURL;
            filenamePrefix = "TRAILER_";
        }
        if (!StringUtils.isEmpty(filename)) {
            if (!StringUtils.isEmpty(uploadername)) {
                filename = uploadername + "_" + filename;
            }
            filename = filenamePrefix + filename;
            filename = filename.replaceAll("\\p{Z}", " ");
            // why do we do this?? http://board.jdownloader.org/showthread.php?p=304933#post304933
            // filename = filename.replaceAll("[\\.\\d]{3,}$", "");
            filename = Encoding.htmlDecode(filename);
            filename = filename.trim();
            filename = filename.replaceAll("(:|,|\\s)", "_");
            filename += ".mp4";
            link.setFinalFileName(filename);
        } else if (!link.isNameSet()) {
            /* Fallback */
            link.setName(fid + ".mp4");
        }
        if (!this.getPluginConfig().getBooleanProperty(fastLinkCheck, fastLinkCheck_default) && !StringUtils.isEmpty(httpDownloadurl)) {
            br.getHeaders().put("Referer", null);
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(httpDownloadurl);
                if (looksLikeDownloadableContent(con)) {
                    if (con.getCompleteContentLength() > 0) {
                        link.setDownloadSize(con.getCompleteContentLength());
                        link.setVerifiedFileSize(con.getCompleteContentLength());
                    }
                } else {
                    server_issues = true;
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

    /*
     * IMPORTANT NOTE: Free (unregistered) Users can watch (&download) videos up to 2 hours in length - if videos are longer, users can only
     * watch the first two hours of them - afterwards they will get this message: http://i.snag.gy/FGl1E.jpg
     */
    private void login(final Account account, final boolean verifyCookies, final String checkURL) throws IOException, PluginException, InterruptedException {
        synchronized (account) {
            if (!StringUtils.contains(account.getUser(), "@")) {
                // >XYZ is not an e-mail address.<
                throw new AccountInvalidException("Please enter your E-Mail address as username!");
            }
            prepareBrowser(this.br);
            br.setCookiesExclusive(true);
            br.setFollowRedirects(true);
            final Cookies cookies = account.loadCookies("");
            final Cookies userCookies = Cookies.parseCookiesFromJsonString(account.getPass());
            if (cookies != null) {
                this.br.setCookies(this.getHost(), cookies);
                if (!verifyCookies) {
                    logger.info("Trust cookies without login");
                    return;
                } else {
                    logger.info("Attempting cookie login");
                    br.getPage(checkURL);
                    if (isLoggedINVideoFC2()) {
                        logger.info("Cookie login successful");
                        account.saveCookies(br.getCookies(br.getHost()), "");
                        return;
                    } else {
                        logger.info("Cookie login failed");
                    }
                }
            }
            if (userCookies != null) {
                logger.info("Attempting user-cookie login");
                this.br.setCookies(this.getHost(), userCookies);
                br.getPage("https://video.fc2.com/a/");
                if (isLoggedINVideoFC2()) {
                    logger.info("Cookie user-login successful");
                    account.saveCookies(br.getCookies(br.getHost()), "");
                    return;
                } else {
                    logger.info("Cookie user-login failed");
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "Cookie login failed", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            br.getPage("https://video.fc2.com/");
            br.getPage("https://secure.id.fc2.com/?done=video&switch_language=en");
            /* 2020-12-18: Typically a redirect to: https://fc2.com/en/login.php?ref=video */
            final String redirect = br.getRegex("http-equiv=\"Refresh\" content=\"\\d+; url=(https?://[^<>\"]+)\"").getMatch(0);
            if (redirect != null) {
                br.getPage(redirect);
            }
            final Form loginform = br.getFormbyProperty("name", "form_login");
            if (loginform == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            loginform.put("email", Encoding.urlEncode(account.getUser()));
            loginform.put("pass", Encoding.urlEncode(account.getPass()));
            // loginform.put("Submit.x", new Random().nextInt(100) + "");
            // loginform.put("Submit.y", new Random().nextInt(100) + "");
            loginform.put("image.x", new Random().nextInt(100) + "");
            loginform.put("image.y", new Random().nextInt(100) + "");
            // loginform.remove("image");
            /*
             * "Keep login" functionality is serverside broken? I'm not able to select this on their website/it doesn't get set. --> Let's
             * try it anyways and hope it makes our cookies valid for a longer period of time!
             */
            // loginform.remove("keep_login");
            loginform.put("keep_login", "1");
            if (loginform.hasInputFieldByName("recaptcha")) {
                final DownloadLink dlinkbefore = this.getDownloadLink();
                try {
                    final DownloadLink dl_dummy;
                    if (dlinkbefore != null) {
                        dl_dummy = dlinkbefore;
                    } else {
                        dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                        this.setDownloadLink(dl_dummy);
                    }
                    final String recaptchaV2Response = new CaptchaHelperHostPluginRecaptchaV2(this, br).getToken();
                    loginform.put("recaptcha", Encoding.urlEncode(recaptchaV2Response));
                } finally {
                    this.setDownloadLink(dlinkbefore);
                }
            }
            br.setFollowRedirects(false);
            br.submitForm(loginform);
            {
                /* 2021-01-04: Small workaround for bad redirect to wrong page on 2FA login required */
                final boolean required2FALogin = br.getRedirectLocation() != null && br.getRedirectLocation().contains("login_authentication.php");
                br.followRedirect();
                br.setFollowRedirects(true);
                if (!br.getURL().contains("login_authentication.php") && required2FALogin) {
                    br.getPage("https://secure.id.fc2.com/login_authentication.php");
                }
            }
            /*
             * TODO: 2020-12-17: Check 2FA login handling below as it is untested.
             */
            final Form twoFactorLogin = br.getFormbyActionRegex(".*login_authentication\\.php.*");
            if (twoFactorLogin != null) {
                logger.info("2FA login required");
                final DownloadLink dl_dummy;
                if (this.getDownloadLink() != null) {
                    dl_dummy = this.getDownloadLink();
                } else {
                    dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
                }
                String twoFACode = getUserInput("Enter Google 2-Factor Authentication code?", dl_dummy);
                if (twoFACode != null) {
                    twoFACode = twoFACode.trim();
                }
                if (twoFACode == null || !twoFACode.matches("[A-Za-z0-9]{6}")) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiges Format der 2-faktor-Authentifizierung!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid 2-factor-authentication code format!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                logger.info("Submitting 2FA code");
                twoFactorLogin.put("code", twoFACode);
                br.submitForm(twoFactorLogin);
            }
            /*
             * If everything goes as planned, we should be redirected to video.fc2.com but in case we're not we'll also check logged in
             * state for their main portal fc2.com.
             */
            if (br.getHost(true).equals("fc2.com")) {
                logger.info("Automatic redirect to video.fc2.com after login failed");
                if (!isLoggedINFC2()) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
                br.getPage(checkURL);
            } else {
                logger.info("Automatic redirect to video.fc2.com after login successful");
            }
            if (!br.getURL().equals(checkURL)) {
                logger.info("Accessing target URL: " + checkURL);
                br.getPage(checkURL);
            }
            if (!isLoggedINVideoFC2()) {
                /* This should never happen */
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "fc2 Account is valid but service 'video.fc2.com' has not been added yet.", PluginException.VALUE_ID_PREMIUM_TEMP_DISABLE);
            }
            account.saveCookies(this.br.getCookies(this.getHost()), "");
        }
    }

    private boolean isLoggedINFC2() {
        return br.containsHTML("/logout\\.php");
    }

    private boolean isLoggedINVideoFC2() {
        return br.containsHTML("/logoff\\.php");
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        synchronized (account) {
            final AccountInfo ai = new AccountInfo();
            this.login(account, true, "https://" + this.getHost() + "/payment/premium/");
            /* Switch to english language */
            final String userSelectedLanguage = br.getCookie(this.getHost(), "language", Cookies.NOTDELETEDPATTERN);
            if (!StringUtils.equalsIgnoreCase(userSelectedLanguage, "en")) {
                br.getPage("/a/language_change.php?lang=en");
            }
            if (!br.getURL().contains("/payment/premium/")) {
                br.getPage("/payment/premium/");
            }
            /* Check for multiple traits - we want to make sure that we correctly recognize premium accounts! */
            boolean isPremium = br.containsHTML("class=\"c-header_main_mamberType\"[^>]*><span[^>]*>Premium|>\\s*Contract Extension|>\\s*Premium Member account information");
            String expire = br.getRegex("(\\d{4}/\\d{2}/\\d{2})[^>]*Automatic renewal date").getMatch(0);
            if (!isPremium) {
                isPremium = expire != null;
            }
            if (isPremium) {
                /* Only set expire date if we find one */
                if (expire != null) {
                    ai.setValidUntil(TimeFormatter.getMilliSeconds(expire, "yyyy/MM/dd", Locale.ENGLISH), this.br);
                }
                ai.setStatus("Premium Account");
                account.setType(AccountType.PREMIUM);
            } else {
                account.setType(AccountType.FREE);
            }
            ai.setUnlimitedTraffic();
            /* Switch back to users' previously set language if that != en */
            if (userSelectedLanguage != null && !userSelectedLanguage.equalsIgnoreCase("en")) {
                logger.info("Switching back to users preferred language: " + userSelectedLanguage);
                br.getPage("/a/language_change.php?lang=" + userSelectedLanguage);
            }
            return ai;
        }
    }

    private void doDownload(final Account account, final DownloadLink link) throws Exception {
        /* OLD-API handling */
        final String error = br.getRegex("^err_code=(\\d+)").getMatch(0);
        if (error != null) {
            switch (Integer.parseInt(error)) {
            case 503:
                // :-)
                break;
            case 601:
                /* reconnect */
                logger.info("video.fc2.com: reconnect is needed!");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Limit reached or IP already loading");
            case 602:
                /* reconnect */
                logger.info("video.fc2.com: reconnect is needed!");
                throw new PluginException(LinkStatus.ERROR_IP_BLOCKED, "Limit reached or IP already loading");
            case 603:
                link.setProperty(PROPERTY_PREMIUMONLY, true);
                break;
            default:
                logger.info("video.fc2.com: Unknown error code: " + error);
            }
        }
        if (this.server_issues) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown server error", 10 * 60 * 1000l);
        } else if (StringUtils.isEmpty(this.httpDownloadurl) && StringUtils.isEmpty(this.hlsMaster)) {
            if (!StringUtils.isEmpty(this.trailerURL)) {
                /* Even premium accounts won't be able to watch such content - it has to be bought separately! */
                logger.info("This content needs to be purchased individually otherwise only a trailer is available!");
                // throw new AccountRequiredException();
                throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Only trailer available. Enable trailer download in settings to allow trailer downloads.");
            } else {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
        } else if (onlyForPremiumUsers(link)) {
            throw new AccountRequiredException();
        }
        /* Only download HLS streams if no http download is available */
        if (StringUtils.isEmpty(this.httpDownloadurl)) {
            /* hls download */
            br.getPage(this.hlsMaster);
            checkFFmpeg(link, "Download a HLS Stream");
            dl = new HLSDownloader(link, br, this.hlsMaster);
            dl.startDownload();
        } else {
            /* http download */
            dl = new jd.plugins.BrowserAdapter().openDownload(br, link, this.httpDownloadurl, true, -4);
            if (br.getHttpConnection() != null && br.getHttpConnection().getResponseCode() == 503 && requestHeadersHasKeyNValueContains(br, "server", "nginx")) {
                try {
                    br.followConnection(true);
                } catch (IOException e) {
                    logger.log(e);
                }
                throw new PluginException(LinkStatus.ERROR_RETRY, "Service unavailable. Try again later.", 5 * 60 * 1000l);
            } else if (!looksLikeDownloadableContent(dl.getConnection())) {
                logger.warning("The dllink seems not to be a file!");
                try {
                    br.followConnection(true);
                } catch (IOException e) {
                    logger.log(e);
                }
                if (br.containsHTML("not found")) {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Server error", 60 * 60 * 1000l);
                } else {
                    throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Unknown download error");
                }
            }
            dl.startDownload();
        }
    }

    @Override
    public void handleFree(DownloadLink link) throws Exception {
        requestFileInformation(link);
        doDownload(null, link);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link, account);
        doDownload(account, link);
    }

    private boolean isOffline(final String linkid) {
        return br.getHttpConnection().getResponseCode() == 404 || br.getURL().contains("err.php") || !br.getURL().contains(linkid) || br.toString().contains("(Removed) **************");
    }

    private String getMimi(String s) {
        return JDHash.getMD5(s + "_" + "gGddgPfeaf_gzyr");
    }

    private String getKey() {
        String javaScript = br.getRegex("eval(\\(f.*?)[\r\n]+").getMatch(0);
        if (javaScript == null) {
            javaScript = br.getRegex("(var __[0-9a-zA-Z]+ = \'undefined\'.*?\\})[\r\n]+\\-\\->").getMatch(0);
        }
        if (javaScript == null) {
            return null;
        }
        Object result = new Object();
        ScriptEngineManager manager = JavaScriptEngineFactory.getScriptEngineManager(this);
        ScriptEngine engine = manager.getEngineByName("javascript");
        Invocable inv = (Invocable) engine;
        try {
            if (!javaScript.startsWith("var")) {
                engine.eval(engine.eval(javaScript).toString());
            }
            engine.eval(javaScript);
            engine.eval("var window = new Object();");
            result = inv.invokeFunction("getKey");
        } catch (Throwable e) {
            return null;
        }
        return result != null ? result.toString() : null;
    }

    private void prepareFinalLink() {
        if (httpDownloadurl != null) {
            httpDownloadurl = httpDownloadurl.replaceAll("\\&mid=", "?mid=");
            String t = new Regex(httpDownloadurl, "cdnt=(\\d+)").getMatch(0);
            String h = new Regex(httpDownloadurl, "cdnh=([0-9a-f]+)").getMatch(0);
            httpDownloadurl = new Regex(httpDownloadurl, "(.*?)\\&sec=").getMatch(0);
            if (t != null && h != null) {
                httpDownloadurl = httpDownloadurl + "&px-time=" + t + "&px-hash=" + h;
            }
        }
    }

    private boolean onlyForPremiumUsers(final DownloadLink link) {
        return link.getBooleanProperty(PROPERTY_PREMIUMONLY, false);
    }

    /**
     * If import Browser request headerfield contains key of k && key value of v
     *
     * @author raztoki
     */
    private boolean requestHeadersHasKeyNValueContains(final Browser ibr, final String k, final String v) {
        if (k == null || v == null || ibr == null || ibr.getHttpConnection() == null) {
            return false;
        }
        if (ibr.getHttpConnection().getHeaderField(k) != null && ibr.getHttpConnection().getHeaderField(k).toLowerCase(Locale.ENGLISH).contains(v.toLowerCase(Locale.ENGLISH))) {
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}