package local.visualize;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** File export shared by the code header's native actions. */
public final class CodeActions {
    static String normalized(String language) {
        return language == null ? "" : language.trim().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
    }

    public static boolean isHtml(String language) {
        String name = normalized(language);
        return name.equals("html") || name.equals("htm") || name.equals("html5") || name.equals("text/html");
    }

    static String extension(String language) {
        String name = normalized(language);
        if (isHtml(name)) return "html";
        switch (name) {
            case "javascript": case "js": return "js";
            case "typescript": case "ts": return "ts";
            case "python": case "py": return "py";
            case "c++": case "cpp": return "cpp";
            case "c#": case "csharp": case "cs": return "cs";
            case "shell": case "bash": case "sh": case "zsh": return "sh";
            case "powershell": case "ps1": return "ps1";
            case "yaml": case "yml": return "yaml";
            case "markdown": case "md": return "md";
            case "kotlin": case "kt": return "kt";
            case "rust": case "rs": return "rs";
            case "ruby": case "rb": return "rb";
            case "visualize": case "json": return "json";
            case "java": case "css": case "xml": case "svg": case "sql": case "c":
            case "h": case "go": case "php": case "swift": case "dart": case "jsx":
            case "tsx": case "vue": case "r": case "lua": case "toml": case "ini":
            case "csv": case "bat": case "tex": return name;
            case "mermaid": return "mmd";
            default: return "txt";
        }
    }

    public static void open(Context context, String language, String source, boolean preview) {
        try {
            // Pass a private cache token, not the source: large replies exceed Binder limits.
            File directory = new File(context.getCacheDir(), "code-files");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException();
            File[] oldFiles = directory.listFiles();
            if (oldFiles != null) for (File old : oldFiles) {
                if (System.currentTimeMillis() - old.lastModified() > 7L * 86400000L) old.delete();
            }
            File file = File.createTempFile("code-", ".utf8", directory);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(source.getBytes(StandardCharsets.UTF_8));
            }
            Intent intent = new Intent(context, CodeActivity.class);
            intent.putExtra("token", file.getName());
            intent.putExtra("extension", extension(language));
            intent.putExtra("preview", preview);
            context.startActivity(intent);
        } catch (Exception failure) {
            Toast.makeText(context, "无法打开代码，请重试", Toast.LENGTH_LONG).show();
        }
    }
}
