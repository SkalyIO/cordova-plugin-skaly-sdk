package io.skaly.cordova.sdk;

import android.app.Activity;
import android.content.Intent;
import android.os.Handler;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import io.skaly.sdk.Sex;
import io.skaly.sdk.Skaly;
import io.skaly.sdk.watches.WatchReply;
import io.skaly.sdk.watches.WatchReplyKt;
import kotlin.Pair;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function0;
import io.skaly.sdk.scales.ScaleReply;
import io.skaly.sdk.scales.ScaleReplyKt;

public class SkalySDK extends CordovaPlugin {
    public static final String TAG = "SkalySDK";

    private static Skaly skaly;
    private Activity activity;

    /**
     * Constructor.
     */
    public SkalySDK() {
    }

    /**
     * Sets the context of the Command. This can then be used to do things like
     * get file paths associated with the Activity.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.activity = cordova.getActivity();
        cordova.setActivityResultCallback(this);
    }

    /**
     * Executes the request and returns PluginResult.
     *
     * @param action          The action to execute.
     * @param args            JSONArry of arguments for the plugin.
     * @param callbackContext The callback id used when calling back into JavaScript.
     * @return True if the action was valid, false if not.
     */
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        Handler mainHandler = new Handler(activity.getMainLooper());
        if ("start".equals(action)) {
            String rootKey = args.getString(0);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly = new Skaly(SkalySDK.this.activity, rootKey);
                    callbackContext.success();
                }
            });
        }
        // "Has" device commands
        else if ("hasScale".equals(action)) {
            JSONObject json = new JSONObject();
            json.put("hasScale", skaly.hasScale());
            callbackContext.success(json);
        } else if ("hasWatch".equals(action)) {
            JSONObject json = new JSONObject();
            json.put("hasWatch", skaly.hasWatch());
            callbackContext.success(json);
        }
        // end "has" device commands
        else if ("addScale".equals(action)) {
            String supportedScalesStr = args.getString(0);
            String[] supportedScales = new String[0];
            if (supportedScalesStr.length() > 0) {
                supportedScales = supportedScalesStr.split(",");
            }
            final List<String> supportedScalesList = Arrays.asList(supportedScales);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly.addScale(supportedScalesList, new Function1<Boolean, Unit>() {
                        @Override
                        public Unit invoke(Boolean success) {
                            if (success) {
                                callbackContext.success();
                            } else {
                                callbackContext.error("Failed adding scale");
                            }
                            return null;
                        }
                    });
                }
            });
        } else if ("addWatch".equals(action)) {
            String supportedWatchStr = args.getString(0);
            String[] supportedWatches = new String[0];
            if (supportedWatchStr.length() > 0) {
                supportedWatches = supportedWatchStr.split(",");
            }
            final List<String> supportedWatchesList = Arrays.asList(supportedWatches);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly.addWatch(supportedWatchesList, new Function1<Boolean, Unit>() {
                        @Override
                        public Unit invoke(Boolean success) {
                            if (success) {
                                callbackContext.success();
                            } else {
                                callbackContext.error("Failed adding watch");
                            }
                            return null;
                        }
                    });
                }
            });
        } else if ("addIdentity".equals(action)) {
            String handle = args.getString(0);
            Sex sex = args.getInt(1) == 0 ? Sex.Male : Sex.Female;
            Date birthday = new Date(args.getLong(2) * 1000);
            int length = args.getInt(3);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        skaly.addIdentity(handle, sex, birthday, length, new Function1<Boolean, Unit>() {
                            @Override
                            public Unit invoke(Boolean success) {
                                if (success) {
                                    callbackContext.success();
                                } else {
                                    callbackContext.error("Failed adding user");
                                }
                                return null;
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } else if ("startReading".equals(action)) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly.startReading(new kotlin.jvm.functions.Function3<Boolean, String, ScaleReply, Unit>() {
                        @Override
                        public Unit invoke(Boolean success, String scannedHandle, ScaleReply scaleReply) {
                            try {
                                if (scaleReply == null) {
                                    callbackContext.error("Didn't get a reading, handle (contains error message): " + scannedHandle);
                                    return null;
                                }
                                JSONObject json = new JSONObject();
                                json.put("scaleReply", new JSONObject(ScaleReplyKt.toJSONString(scaleReply)));
                                json.put("scannedHandle", scannedHandle);
                                callbackContext.success(json);
                            } catch (Exception e) {
                                e.printStackTrace();
                                callbackContext.error(e.getMessage());
                            }
                            return null;
                        }
                    });
                }
            });
        } else if ("getScaleData".equals(action)) {
            if (skaly.getHandles().length == 0) {
                callbackContext.error("No handles registered yet!");
                return false;
            }
            String handle = args.getString(0) == null ? skaly.getHandles()[0] : args.getString(0);
            Long fromUID = args.getLong(1);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        Pair<Long, List<ScaleReply>> replies;
                        if(fromUID == 0) {
                            replies = skaly.userScaleData(handle);
                        }
                        else {
                            replies = skaly.userScaleDataBeforeHandle(handle, fromUID);
                        }
                        
                        JSONObject json = new JSONObject();
                        JSONArray scaleRepliesJsonArr = new JSONArray();
                        for (ScaleReply sr : replies.getSecond()) {
                            scaleRepliesJsonArr.put(new JSONObject(ScaleReplyKt.toJSONString(sr)));
                        }
                        json.put("scaleReplies", scaleRepliesJsonArr);
                        json.put("lastUID", replies.getFirst());
                        callbackContext.success(json);
                    } catch (Exception e) {
                        e.printStackTrace();
                        callbackContext.error(e.getMessage());
                    }
                }
            });
        } else if ("getWatchData".equals(action)) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly.startReadingWatch((success, watchReplies) -> {
                        try {
                            JSONArray watchRepliesJsonArr = new JSONArray();
                            for (WatchReply wr : watchReplies) {
                                watchRepliesJsonArr.put(new JSONObject(WatchReplyKt.toJSONString(wr)));
                            }
                            JSONObject json = new JSONObject();
                            json.put("watchReplies", watchRepliesJsonArr);
                            callbackContext.success(json);
                        } catch (Exception e) {
                            e.printStackTrace();
                            callbackContext.error(e.getMessage());
                        }
                        return Unit.INSTANCE;
                    });
                }
            });
        } else if ("allowAccessToData".equals(action)) {
            String to = args.getString(0);
            String handle = args.getString(1);
            mainHandler.post(new Runnable() {
                @Override
                public void run() {
                    skaly.allowAccessToData(to, handle, new Function0<Unit>() {
                        @Override
                        public Unit invoke() {
                            callbackContext.success();
                            return null;
                        }
                    });
                }
            });
        } else {
            return false;
        }
        return true;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        skaly.onActivityResult(requestCode, resultCode, data);
    }

}
