package jd.plugins.components;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.plugins.DownloadLink;

public class PornEmbedParser {

    /**
     * This method is designed to find embedded porn urls in html code.
     *
     * @param br
     *            : Browser containing the sourceurl with the embed urls/codes *
     *
     *
     * @param title
     *            : Title to be used in case a directhttp url is found. If the title is not given, directhttp urls will never be decrypted.
     *
     *
     */
    public static ArrayList<DownloadLink> findEmbedUrls(final Browser br, String title) throws IOException {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final Browser brdecrypt = br.cloneBrowser();
        // xvideos.com 1
        String externID = br.getRegex("xvideos\\.com/embedframe/(\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.xvideos.com/video" + externID));
            return decryptedLinks;
        }
        // xvideos.com 2
        externID = br.getRegex("\"(http://(www\\.)?flashservice\\.xvideos\\.com/embedframe/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("madthumbs\\.com%2Fvideos%2Fembed_config%3Fid%3D(\\d+)").getMatch(0);
        if (externID != null) {
            DownloadLink dl = createDownloadlink("http://www.madthumbs.com/videos/amateur/" + new Random().nextInt(100000) + "/" + externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("(\"|\\')(http://(www\\.)?tube8\\.com/embed/[^<>\"/]*?/[^<>\"/]*?/\\d+/?)(\"|\\')").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID.replace("tube8.com/embed/", "tube8.com/")));
            return decryptedLinks;
        }
        externID = br.getRegex("redtube\\.com/player/\"><param name=\"FlashVars\" value=\"id=(\\d+)\\&").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("embed\\.redtube\\.com/player/\\?id=(\\d+)\\&").getMatch(0);
        }
        if (externID == null) {
            externID = br.getRegex("https?://(?:www\\.)?embed\\.redtube\\.com/\\?id=(\\d+)").getMatch(0);
        }
        // drtuber.com embed v3
        externID = br.getRegex("(http://(www\\.)?drtuber\\.com/player/config_embed3\\.php\\?vkey=[a-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        // drtuber.com embed v4
        externID = br.getRegex("\"(http://(www\\.)?drtuber\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?xhamster\\.com/xembed\\.php\\?video=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("emb\\.slutload\\.com/([A-Za-z0-9]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://slutload.com/watch/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("pornerbros\\.com/content/(\\d+)\\.xml").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.pornerbros.com/" + externID + "/" + System.currentTimeMillis() + ".html"));
            return decryptedLinks;
        }
        externID = br.getRegex("hardsextube\\.com/embed/(\\d+)/").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.hardsextube.com/video/" + externID + "/"));
            return decryptedLinks;
        }
        externID = br.getRegex("embed\\.pornrabbit\\.com/player\\.swf\\?movie_id=(\\d+)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("pornrabbit\\.com/embed/(\\d+)").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://pornrabbit.com/video/" + externID + "/"));
            return decryptedLinks;
        }
        /* tnaflix.com handling #1 */
        externID = br.getRegex("player\\.tnaflix\\.com/video/(\\d+)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("http://www.tnaflix.com/teen-porn/" + System.currentTimeMillis() + "/video" + externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        /* tnaflix.com handling #2 */
        externID = br.getRegex("tnaflix\\.com/embedding_player/player_[^<>\"]+\\.swf\".*?value=\"config=(embedding_feed\\.php\\?viewkey=[a-z0-9]+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("https://www.tnaflix.com/embedding_player/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("metacafe\\.com/fplayer/(\\d+)/").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.metacafe.com/watch/" + externID + "/" + System.currentTimeMillis()));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?pornhub\\.com/embed/[a-z0-9]+)\"").getMatch(0);
        if (externID != null) {
            DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        // pornhub handling number 2
        externID = br.getRegex("name=\"FlashVars\" value=\"options=(http://(www\\.)?pornhub\\.com/embed_player(_v\\d+)?\\.php\\?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            brdecrypt.getPage(externID);
            if (brdecrypt.containsHTML("<link_url>N/A</link_url>") || brdecrypt.containsHTML("No htmlCode read") || brdecrypt.containsHTML(">404 Not Found<")) {
                final DownloadLink offline = createDownloadlink("http://www.pornhub.com/view_video.php?viewkey=" + new Random().nextInt(10000000));
                offline.setName(externID);
                offline.setAvailable(false);
                decryptedLinks.add(offline);
                return decryptedLinks;
            }
            externID = br.getRegex("<link_url>(http://[^<>\"]*?)</link_url>").getMatch(0);
            if (externID == null) {
                return null;
            }
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        // myxvids.com 1
        externID = br.getRegex("\"(http://(www\\.)?myxvids\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        // myxvids.com 2
        externID = br.getRegex("(\\'|\")(http://(www\\.)?myxvids\\.com/embed_code/\\d+/\\d+/myxvids_embed\\.js)(\\'|\")").getMatch(1);
        if (externID != null) {
            br.getPage(externID);
            final String finallink = br.getRegex("\"(http://(www\\.)?myxvids\\.com/embed/\\d+)\"").getMatch(0);
            if (finallink == null) {
                return null;
            }
            decryptedLinks.add(createDownloadlink(finallink));
            return decryptedLinks;
        }
        // empflix.com 1
        externID = br.getRegex("player\\.empflix\\.com/video/(\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.empflix.com/videos/" + System.currentTimeMillis() + "-" + externID + ".html"));
            return decryptedLinks;
        }
        // empflix.com 2
        externID = br.getRegex("empflix\\.com/embedding_player/player[^<>\"/]*?\\.swf\".*?value=\"config=embedding_feed\\.php\\?viewkey=([^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.empflix.com/embedding_player/embedding_feed.php?viewkey=" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("<iframe src=\"http://(www\\.)?yobt\\.tv/embed/(\\d+)\\.html\"").getMatch(1);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.yobtdecrypted.tv/content/" + externID + "/" + System.currentTimeMillis() + ".html"));
            return decryptedLinks;
        }
        externID = br.getRegex("stileproject\\.com/embed/(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://stileproject.com/video/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?deviantclip\\.com/watch/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("webdata\\.vidz\\.com/demo/swf/FlashPlayerV2\\.swf\".*?flashvars=\"id_scene=(\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.vidz.com/video/" + System.currentTimeMillis() + "/vidz_porn_videos/?s=" + externID));
            return decryptedLinks;
        }
        // youporn.com handling 1
        externID = br.getRegex("youporn\\.com/embed/(\\d+)").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("http://www.youporn.com/watch/" + externID + "/" + System.currentTimeMillis());
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("pornative\\.com/embed/player\\.swf\\?movie_id=(\\d+)").getMatch(0);
        if (externID != null) {
            DownloadLink dl = createDownloadlink("http://pornative.com/" + externID + ".html");
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("pornyeah\\.com/playerConfig\\.php\\?[a-z0-9]+\\.[a-z0-9\\.]+\\|(\\d+)").getMatch(0);
        if (externID != null) {
            DownloadLink dl = createDownloadlink("http://www.pornyeah.com/videos/" + Integer.toString(new Random().nextInt(1000000)) + "-" + externID + ".html");
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("(http://(www\\.)?mofosex\\.com/(embed_player\\.php\\?id=|embed\\?videoid=)\\d+)").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?nuvid\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?youjizz\\.com/videos/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?vporn\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?bangyoulater\\.com/embed\\.php\\?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?pornhost\\.com/(embed/)?\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?spankwire\\.com/EmbedPlayer\\.aspx/?\\?ArticleId=\\d+)").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?submityourflicks\\.com/embedded/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("(http://(www\\.)?theamateurzone\\.info/media/player/config_embed\\.php\\?vkey=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?embeds\\.sunporno\\.com/embed/[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            if (externID.equals("http://embeds.sunporno.com/embed/videos")) {
                final DownloadLink offline = createDownloadlink("directhttp://" + externID);
                offline.setAvailable(false);
                offline.setProperty("offline", true);
                decryptedLinks.add(offline);
                return decryptedLinks;
            }
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?fux\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("moviefap\\.com/embedding_player/player.*?value=\"config=(embedding_feed\\.php\\?viewkey=[a-z0-9]+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.moviefap.com/embedding_player/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?embed\\.porntube\\.com/\\d+)\"").getMatch(0);
        if (externID == null) {
            externID = br.getRegex("\"(http://(www\\.)?porntube\\.com/embed/\\d+)\"").getMatch(0);
        }
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?xxxhdd\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?extremetube\\.com/embed/[^<>\"/]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://embeds\\.ah\\-me\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(http://(www\\.)?proporn\\.com/embed/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(https?://(?:www\\.)?spankbang\\.com/[A-Za-z0-9]+/embed/)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(https?://(?:www\\.)?desihoes\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(https?://(?:www\\.)?sexbot\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"(https?://(?:www\\.)?xxxtube\\.com/embed/\\d+/?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("\"http://(www\\.)?freeviewmovies\\.com/embed/(\\d+)/\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www.freeviewmoviesdecrypted/video/" + externID));
            return decryptedLinks;
        }
        externID = br.getRegex("(http://(www\\.)?gasxxx\\.com/media/player/config_embed\\.php\\?vkey=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("(http://(www\\.)?keezmovies\\.com/embed_player\\.php\\?v?id=\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("(foxytube\\.com/embedded/\\d+)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink("http://www." + externID));
            return decryptedLinks;
        }
        // filename needed for all IDs below
        if (title == null) {
            return null;
        }
        /* TODO: Remove as much Browser-accesses as possible, handle all embedded urls in the corresponding host plugins! */
        title = Encoding.htmlDecode(title).trim();
        title = encodeUnicode(title);
        externID = br.getRegex("shufuni\\.com/Flash/.*?flashvars=\"VideoCode=(.*?)\"").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink("http://www.shufuni.com/handlers/FLVStreamingv2.ashx?videoCode=" + externID);
            dl.setFinalFileName(Encoding.htmlDecode(title.trim()));
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("src=\"http://videos\\.allelitepass\\.com/txc/([^<>\"/]*?)\\.swf\"").getMatch(0);
        if (externID != null) {
            brdecrypt.getPage("http://videos.allelitepass.com/txc/player.php?video=" + Encoding.htmlDecode(externID));
            externID = brdecrypt.getRegex("<file>(http://[^<>\"]*?)</file>").getMatch(0);
            if (externID != null) {
                final DownloadLink dl = createDownloadlink("directhttp://" + externID);
                dl.setFinalFileName(title + ".flv");
                decryptedLinks.add(dl);
                return decryptedLinks;
            }

        }
        // youporn.com handling 2
        externID = br.getRegex("flashvars=\"file=(http%3A%2F%2Fdownload\\.youporn\\.com[^<>\"]*?)\\&").getMatch(0);
        if (externID != null) {
            brdecrypt.setCookie("http://youporn.com/", "age_verified", "1");
            brdecrypt.setCookie("http://youporn.com/", "is_pc", "1");
            brdecrypt.setCookie("http://youporn.com/", "language", "en");
            brdecrypt.getPage(Encoding.htmlDecode(externID));
            if (brdecrypt.getRequest().getHttpConnection().getResponseCode() == 404) {
                return null;
            }
            if (brdecrypt.containsHTML("download\\.youporn\\.com/agecheck")) {
                return decryptedLinks;
            }
            externID = brdecrypt.getRegex("\"(http://(www\\.)?download\\.youporn.com/download/\\d+/\\?xml=1)\"").getMatch(0);
            if (externID == null) {
                return null;
            }
            brdecrypt.getPage(externID);
            final String finallink = brdecrypt.getRegex("<location>(http://.*?)</location>").getMatch(0);
            if (finallink == null) {
                return null;
            }
            final DownloadLink dl = createDownloadlink("directhttp://" + Encoding.htmlDecode(finallink));
            String type = brdecrypt.getRegex("<meta rel=\"type\">(.*?)</meta>").getMatch(0);
            if (type == null) {
                type = "flv";
            }
            dl.setFinalFileName(title + "." + type);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        externID = br.getRegex("(http://(www\\.)?5ilthy\\.com/playerConfig\\.php\\?[a-z0-9]+\\.(flv|mp4))").getMatch(0);
        if (externID != null) {
            final DownloadLink dl = createDownloadlink(externID);
            dl.setProperty("5ilthydirectfilename", title);
            decryptedLinks.add(dl);
            return decryptedLinks;

        }
        return null;
    }

    /** Avoid chars which are not allowed in filenames under certain OS' */
    private static String encodeUnicode(final String input) {
        String output = input;
        output = output.replace(":", ";");
        output = output.replace("|", "¦");
        output = output.replace("<", "[");
        output = output.replace(">", "]");
        output = output.replace("/", "⁄");
        output = output.replace("\\", "∖");
        output = output.replace("*", "#");
        output = output.replace("?", "¿");
        output = output.replace("!", "¡");
        output = output.replace("\"", "'");
        return output;
    }

    protected static DownloadLink createDownloadlink(final String link) {
        final String host = jd.http.Browser.getHost(link);
        return new DownloadLink(null, null, host, Encoding.urlDecode(link, true), true);
    }
}
