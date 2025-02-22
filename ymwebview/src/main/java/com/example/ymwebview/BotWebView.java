package com.example.ymwebview;



import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;

import android.os.CountDownTimer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.FragmentManager;

import com.example.ymwebview.models.BotEventsModel;
import com.example.ymwebview.models.ConfigDataModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.skyfishjy.library.RippleBackground;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class BotWebView extends AppCompatActivity {
    private final String TAG = "YM WebView Plugin";
    WebviewOverlay fh;
    private boolean willStartMic = false;
    public String postUrl= "https://app.yellowmessenger.com/api/chat/upload?bot=";


    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");


    public void startMic(long countdown_time){
        RelativeLayout voiceArea = findViewById(R.id.voiceArea);
        if(!willStartMic){
        willStartMic = true;
            new CountDownTimer(countdown_time, 1000) {
                public void onTick(long millisUntilFinished) {}
                @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
                public void onFinish() {
                    if(voiceArea.getVisibility() == View.INVISIBLE && willStartMic){
                        toggleBottomSheet();
                    }
                }
            }.start();
        }
    }

    public void closeBot(){
        fh.closeBot();
    }

    public void setActionBarColor(){
        try{
            String color = ConfigDataModel.getInstance().getConfig("actionBarColor");
            boolean isHexCode = color.matches("-?[0-9a-fA-F]+");
            int customColor = -1;
            try {
                customColor = Integer.parseInt(color);
            }
            catch (Exception e){
                Log.d(TAG, e.getMessage());
            }

            if(customColor != -1) {


                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    ActionBar actionBar = BotWebView.this.getSupportActionBar();
                    if (actionBar != null) {
                        actionBar.setBackgroundDrawable(new ColorDrawable(customColor));
                    }

                }
            }
        }
        catch (Exception e){

            Log.d(TAG, "Incorrect color code for App bar.");
        }
    }

    public void setOverviewColor(){
        try{
        String color = ConfigDataModel.getInstance().getConfig("actionBarColor");

        boolean isHexCode = color.matches("-?[0-9a-fA-F]+");
        int customColor = -1;
        try {
            customColor =  Integer.parseInt(color);
        }
        catch (Exception e){
            Log.d(TAG, e.getMessage());
        }

        if(customColor != -1) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                ActivityManager.TaskDescription td = new ActivityManager.TaskDescription(null, null, customColor);
                 this.setTaskDescription(td);
            }
        }
    }
        catch (Exception e){

        Log.d(TAG, "Incorrect color code for overview title bar.");
    }



    }



    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setActionBarColor();
        setOverviewColor();

        // setting up local listener
        Log.d(TAG, "onCreate: setting up local listener");
        YMBotPlugin.getInstance().setLocalListener(new BotEventListener() {
            @Override
            public void onSuccess(BotEventsModel botEvent) {
                Log.d(TAG, "onSuccess: "+botEvent.getCode());

                switch (botEvent.getCode()){
                    case "upload-image" :
                        Log.d(TAG, "onSuccess: got event");
                        Map<String, Object> retMap = new Gson().fromJson(
                                botEvent.getData(), new TypeToken<HashMap<String, Object>>() {}.getType());
                         if(retMap.containsKey("uid")){
                             String uId = retMap.get("uid").toString();

                             runUpload(uId);

                         }
                        break;
                }
            }
            @Override
            public void onFailure(String error) {
            }
        });


        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
                    params.bottomMargin = insets.getSystemWindowInsetBottom();
                    return insets.consumeSystemWindowInsets();
                });
        setContentView(R.layout.activity_bot_web_view);

        fh=new WebviewOverlay();
        FragmentManager fragManager=getSupportFragmentManager();
        fragManager.beginTransaction()
                .add(R.id.container,fh)
                .commit();
        String enableSpeech = ConfigDataModel.getInstance().getConfig("enableSpeech");
        if(Boolean.parseBoolean(enableSpeech)){
            FloatingActionButton micButton = findViewById(R.id.floatingActionButton);
            micButton.setVisibility(View.VISIBLE);
            micButton.setOnClickListener(view -> {
                toggleBottomSheet();
            });
        }

        ImageButton backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(view->{
            YMBotPlugin.getInstance().emitEvent(new BotEventsModel("bot-closed",""));
            fh.closeBot();
            this.finish();
        });

    }

    @Override
    public void onResume() {
        super.onResume();
        setOverviewColor();
    }


    public void runUpload(String uid){
        try {
            String botId = ConfigDataModel.getInstance().getConfig("botID");
            postUrl= postUrl + botId + "&uid="+uid+"&secure=false";
            run();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void run() throws IOException {

        OkHttpClient client = new OkHttpClient();

        String imagePath = ConfigDataModel.getInstance().getCustomDataByKey("imagePath");
        Log.d(TAG, "run: "+imagePath);

        File sourceFile = new File(imagePath);

        Log.d(TAG, "File...::::" + sourceFile + " : " + sourceFile.exists());

        final MediaType MEDIA_TYPE = imagePath.endsWith("png") ?
                MediaType.parse("image/png") : MediaType.parse("image/jpeg");
        Log.d(TAG, "run: "+postUrl);
        Log.d(TAG, sourceFile.getName()+"."+MEDIA_TYPE.subtype());


        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("images", sourceFile.getName()+"."+MEDIA_TYPE.subtype(), RequestBody.create(MEDIA_TYPE, sourceFile))
                .build();

        Request request = new Request.Builder()
                .url(postUrl)
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                call.cancel();
                Log.d("Upload", "Can't upload");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {

                final String myResponse = response.body().string();

                BotWebView.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Upload", myResponse);
                    }
                });

            }
        });
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        YMBotPlugin.getInstance().emitEvent(new BotEventsModel("bot-closed",""));
        fh.closeBot();
        this.finish();
    }

    private void speechRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.ENGLISH);
        startActivityForResult(intent, 100);

    }

    SpeechRecognizer sr;

    public void startListeningWithoutDialog() {
        // Intent to listen to user vocal input and return the result to the same activity.
        Context appContext = getApplicationContext();
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        // Use a language model based on free-form speech recognition.
        Map payload = ConfigDataModel.getInstance().getPayload();
        String defaultLanguage = (String) payload.get("defaultLanguage");
        if(defaultLanguage == null){
            defaultLanguage = "en";
        }
        Log.d(TAG, "startListeningWithoutDialog: " + defaultLanguage);
        String languagePref = defaultLanguage;
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, languagePref);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languagePref);
        intent.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, languagePref);



        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,
                appContext.getPackageName());

        // Add custom listeners.

        sr = SpeechRecognizer.createSpeechRecognizer(appContext);
        CustomRecognitionListener listener = new CustomRecognitionListener();
        sr.setRecognitionListener(listener);
        sr.startListening(intent);
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public  void toggleBottomSheet() {

        final RippleBackground rippleBackground=(RippleBackground)findViewById(R.id.animated_btn);
        RelativeLayout voiceArea = findViewById(R.id.voiceArea);
        FloatingActionButton micButton = findViewById(R.id.floatingActionButton);
        TextView textView = findViewById(R.id.speechTranscription);

        if(voiceArea.getVisibility() == View.INVISIBLE){
            textView.setText("I'm listening...");
            willStartMic = false;
            voiceArea.setVisibility(View.VISIBLE);
            rippleBackground.startRippleAnimation();
            startListeningWithoutDialog();

            micButton.setImageDrawable(getDrawable(R.drawable.ic_back_button));
        }else {
            voiceArea.setVisibility(View.INVISIBLE);
            rippleBackground.stopRippleAnimation();
            micButton.setImageDrawable(getDrawable(R.drawable.ic_mic_button));
            sr.stopListening();
        }


    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void closeVoiceArea(){
        final RippleBackground rippleBackground=(RippleBackground)findViewById(R.id.animated_btn);
        RelativeLayout voiceArea = findViewById(R.id.voiceArea);
        FloatingActionButton micButton = findViewById(R.id.floatingActionButton);
        TextView textView = findViewById(R.id.speechTranscription);

            voiceArea.setVisibility(View.INVISIBLE);
            rippleBackground.stopRippleAnimation();
            micButton.setImageDrawable(getDrawable(R.drawable.ic_mic_button));
            sr.stopListening();
            sr.destroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

            super.onActivityResult(requestCode, resultCode, data);
            if (fh != null) {
                Log.d("BotWebView", "onActivityResult is being called");
                fh.onActivityResult(requestCode, resultCode, data);
            }


        if (resultCode == RESULT_OK && data != null) {
            switch (requestCode) {
                case 100:
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    fh.sendEvent(result.get(0));
                    break;
            }
        }
        else if(null == data){

        }
        else {
            Toast.makeText(getApplicationContext(), "Failed to recognize speech!", Toast.LENGTH_LONG).show();
        }
    }
    private String speech_result = "";


    class CustomRecognitionListener implements RecognitionListener {
        boolean singleResult = true;

        private static final String TAG = "RecognitionListener";


        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech");
        }

        public void onBeginningOfSpeech() {

        }

        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged");

        }

        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived");
        }

        public void onEndOfSpeech() {
            Log.d(TAG, "onEndofSpeech");
        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void onError(int error) {
            closeVoiceArea();
            View parentLayout = findViewById(android.R.id.content);
            Snackbar snackbar = Snackbar
                    .make(parentLayout, "We've encountered an error. Please press Mic to continue with voice input.", Snackbar.LENGTH_LONG);
            snackbar.show() ;

        }

        @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
        public void onResults(Bundle results) {
            ArrayList<String> result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            TextView textView = findViewById(R.id.speechTranscription);
            textView.setText(result.get(0));

            if (singleResult) {
                if (result != null && result.size() > 0) {
                    speech_result = result.get(0);
                    sr.cancel();
                    fh.sendEvent(result.get(0));
                }
                closeVoiceArea();
                singleResult=false;
            }


        }

        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults "+partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0));
            TextView textView = findViewById(R.id.speechTranscription);
            textView.setText(partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).get(0));
        }

        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent " + eventType);
        }
    }


}

