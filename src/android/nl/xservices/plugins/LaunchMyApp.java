package nl.xservices.plugins;

import android.content.Intent;
import android.util.Patterns;
import android.util.Log;
import android.os.Bundle;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaActivity;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Iterator;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class LaunchMyApp extends CordovaPlugin {

  private static final String ACTION_CHECKINTENT = "checkIntent";
  private static final String ACTION_CLEARINTENT = "clearIntent";
  private static final String ACTION_GETLASTINTENT = "getLastIntent";

  private String lastIntentString = null;
  private JSONObject lastIntent = null;

  /**
   * We don't want to interfere with other plugins requiring the intent data,
   * but in case of a multi-page app your app may receive the same intent data
   * multiple times, that's why you'll get an option to reset it (null it).
   *
   * Add this to config.xml to enable that behaviour (default false):
   *   <preference name="CustomURLSchemePluginClearsAndroidIntent" value="true"/>
   */
  private boolean resetIntent;

  @Override
  public void initialize(final CordovaInterface cordova, CordovaWebView webView){
    this.resetIntent = preferences.getBoolean("resetIntent", false) ||
        preferences.getBoolean("CustomURLSchemePluginClearsAndroidIntent", false);
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (ACTION_CLEARINTENT.equalsIgnoreCase(action)) {
      final Intent intent = ((CordovaActivity) this.webView.getContext()).getIntent();
      if (resetIntent){
        intent.setData(null);
      }
      return true;
    } else if (ACTION_CHECKINTENT.equalsIgnoreCase(action)) {
      final Intent intent = ((CordovaActivity) this.webView.getContext()).getIntent();
      final String intentString = intent.getDataString();
      if (intentString != null && intent.getScheme() != null) {
        // Send back the JSON
        JSONObject json = parseIntent(intent);
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, json));
      } else {
        callbackContext.error("App was not started via the launchmyapp URL scheme. Ignoring this errorcallback is the best approach.");
      }
      return true;
    } else if (ACTION_GETLASTINTENT.equalsIgnoreCase(action)) {
      if(lastIntentString != null) {
        callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, lastIntent));
      } else {
        callbackContext.error("No intent received so far.");
      }
      return true;
    } else {
      callbackContext.error("This plugin only responds to the " + ACTION_CHECKINTENT + " action.");
      return false;
    }
  }

  @Override
  public void onNewIntent(Intent intent) {
    String intentString = intent.getDataString();
    if (intentString != null && intent.getScheme() != null) {
      // Get the JSON
      JSONObject json = parseIntent(intent);
      
      if (resetIntent){
        intent.setData(null);
      }
      
      try {
        // Use the JSON object
        intentString = json.toString();
        StringWriter writer = new StringWriter(intentString.length() * 2);
        escapeJavaStyleString(writer, intentString, true, false);
        webView.loadUrl("javascript:handleOpenURL('" + URLEncoder.encode(writer.toString()) + "');");
      } catch (IOException ignore) {
      }
    }
  }

  // Parse the intent and data
  private JSONObject parseIntent(Intent intent) {
    JSONObject json = new JSONObject();
    final String intentString = intent.getDataString();
      if (intentString != null && intent.getScheme() != null) {
        
        // Add the intent key
        try {
            json.put("intent", JSONObject.wrap(intent.toString));
        } catch(JSONException e) { }
        try {
            json.put("dataString", JSONObject.wrap(intentString));
        } catch(JSONException e) { }

        // Append others
        try {
          json.put("action", JSONObject.wrap(intent.getAction()));
          json.put("type", JSONObject.wrap(intent.getType()));
          json.put("data", JSONObject.wrap(intent.getData()));
          json.put("package", JSONObject.wrap(intent.getPackage())); 
        } catch(JSONException e) { }
        
        // Add the extra data
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
          Set<String> keys = bundle.keySet();
          for (String key : keys) {
              try {
                  json.put(key, JSONObject.wrap(bundle.get(key)));
              } catch(JSONException e) { }
          }
        }
    
        // Keep the intent;
        lastIntent = json;
        lastIntentString = intentString;
     }
     
     return json;
  }
  
  // Taken from commons StringEscapeUtils
  private static void escapeJavaStyleString(Writer out, String str, boolean escapeSingleQuote,
                                            boolean escapeForwardSlash) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("The Writer must not be null");
    }
    if (str == null) {
      return;
    }
    int sz;
    sz = str.length();
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);

      // handle unicode
      if (ch > 0xfff) {
        out.write("\\u" + hex(ch));
      } else if (ch > 0xff) {
        out.write("\\u0" + hex(ch));
      } else if (ch > 0x7f) {
        out.write("\\u00" + hex(ch));
      } else if (ch < 32) {
        switch (ch) {
          case '\b':
            out.write('\\');
            out.write('b');
            break;
          case '\n':
            out.write('\\');
            out.write('n');
            break;
          case '\t':
            out.write('\\');
            out.write('t');
            break;
          case '\f':
            out.write('\\');
            out.write('f');
            break;
          case '\r':
            out.write('\\');
            out.write('r');
            break;
          default:
            if (ch > 0xf) {
              out.write("\\u00" + hex(ch));
            } else {
              out.write("\\u000" + hex(ch));
            }
            break;
        }
      } else {
        switch (ch) {
          case '\'':
            if (escapeSingleQuote) {
              out.write('\\');
            }
            out.write('\'');
            break;
          case '"':
            out.write('\\');
            out.write('"');
            break;
          case '\\':
            out.write('\\');
            out.write('\\');
            break;
          case '/':
            if (escapeForwardSlash) {
              out.write('\\');
            }
            out.write('/');
            break;
          default:
            out.write(ch);
            break;
        }
      }
    }
  }

  private static String hex(char ch) {
    return Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
  }
}
