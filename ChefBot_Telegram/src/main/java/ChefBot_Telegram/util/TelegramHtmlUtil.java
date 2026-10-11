package ChefBot_Telegram.util;

public final class TelegramHtmlUtil {

    private TelegramHtmlUtil() {}

    public static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }

    public static String bold(String text) {
        return "<b>" + escapeHtml(text) + "</b>";
    }

    public static String italic(String text) {
        return "<i>" + escapeHtml(text) + "</i>";
    }

    public static String code(String text) {
        return "<code>" + escapeHtml(text) + "</code>";
    }

    public static String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "");
    }
}
