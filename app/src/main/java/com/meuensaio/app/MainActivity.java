package com.meuensaio.app;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Meu Ensaio: o app inteiro roda na página (assets/index.html) dentro de um WebView.
 * Esta classe só cuida de: abrir a página, escolher arquivos, receber PDFs
 * compartilhados (WhatsApp, Arquivos...) e o botão Voltar.
 */
public class MainActivity extends Activity {
    private static final int REQ_ARQUIVO = 1;
    private static final String HOST = "appassets.androidplatform.net";

    private WebView web;
    private ValueCallback<Uri[]> retornoArquivo;
    private boolean paginaPronta = false;
    private Intent intentPendente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setTextZoom(100);

        // serve os arquivos de assets/ num endereço https (assim o armazenamento funciona)
        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return loader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception e) {
                    // sem navegador
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                paginaPronta = true;
                if (intentPendente != null) {
                    Intent i = intentPendente;
                    intentPendente = null;
                    receberArquivo(i);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (retornoArquivo != null) retornoArquivo.onReceiveValue(null);
                retornoArquivo = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/pdf", "application/xml", "text/xml", "application/octet-stream",
                        "application/vnd.recordare.musicxml", "application/vnd.recordare.musicxml+xml"});
                try {
                    startActivityForResult(Intent.createChooser(i, "Escolha a partitura"), REQ_ARQUIVO);
                } catch (Exception e) {
                    retornoArquivo = null;
                    return false;
                }
                return true;
            }
        });

        intentPendente = getIntent();
        web.loadUrl("https://" + HOST + "/assets/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ARQUIVO || retornoArquivo == null) return;
        Uri[] r = null;
        if (resultCode == RESULT_OK && data != null && data.getData() != null) r = new Uri[]{data.getData()};
        retornoArquivo.onReceiveValue(r);
        retornoArquivo = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (paginaPronta) receberArquivo(intent);
        else intentPendente = intent;
    }

    /** PDF vindo de "Abrir com" ou "Compartilhar". */
    private void receberArquivo(Intent i) {
        if (i == null || i.getAction() == null) return;
        Uri u = null;
        if (Intent.ACTION_VIEW.equals(i.getAction())) {
            u = i.getData();
        } else if (Intent.ACTION_SEND.equals(i.getAction())) {
            Parcelable p = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (p instanceof Uri) u = (Uri) p;
        }
        if (u == null) return;
        i.setAction(null); // não processa de novo
        final Uri uri = u;
        new Thread(() -> {
            try {
                String nome = nomeDoArquivo(uri);
                byte[] dados = lerTudo(uri);
                final String js = "window.MeuEnsaioAbrir && window.MeuEnsaioAbrir(" + JSONObject.quote(nome) + ",'"
                        + Base64.encodeToString(dados, Base64.NO_WRAP) + "')";
                runOnUiThread(() -> web.evaluateJavascript(js, null));
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "Não consegui abrir o arquivo", Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private String nomeDoArquivo(Uri uri) {
        String nome = null;
        try (Cursor c = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) nome = c.getString(0);
        } catch (Exception e) {
            // ignora
        }
        if (nome == null) nome = uri.getLastPathSegment();
        if (nome == null) nome = "partitura.pdf";
        return nome;
    }

    private byte[] lerTudo(Uri uri) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new Exception("sem dados");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        // a página decide: fecha painel/partitura; se já estiver na biblioteca, minimiza o app
        web.evaluateJavascript("window.MeuEnsaioVoltar ? window.MeuEnsaioVoltar() : false", valor -> {
            if (!"true".equals(valor)) moveTaskToBack(true);
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.evaluateJavascript("window.__ensaio && window.__ensaio.engine && window.__ensaio.engine.playing && window.__ensaio.engine.pause()", null);
    }
}
