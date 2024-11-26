package ai.onnxruntime.genai.demo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import ai.onnxruntime.genai.GenAIException;
import ai.onnxruntime.genai.Generator;
import ai.onnxruntime.genai.GeneratorParams;
import ai.onnxruntime.genai.Sequences;
import ai.onnxruntime.genai.TokenizerStream;
import ai.onnxruntime.genai.demo.databinding.ActivityMainBinding;
import ai.onnxruntime.genai.Model;
import ai.onnxruntime.genai.Tokenizer;

public class MainActivity extends AppCompatActivity implements Consumer<String> {

    private ActivityMainBinding binding;
    private EditText userMsgEdt;
    private Model model;
    private Tokenizer tokenizer;
    private ImageButton sendMsgIB;
    private TextView generatedTV;
    private TextView promptTV;
    private TextView progressText;
    private ImageButton settingsButton;
    private RecyclerView messagesRecyclerView;
    private MessagesAdapter messagesAdapter;
    private List<Message> messagesList;
    private static final String TAG = "genai.demo.MainActivity";
    private int maxLength = 100;
    private float lengthPenalty = 1.0f;

    private static boolean fileExists(Context context, String fileName) {
        File file = new File(context.getFilesDir(), fileName);
        return file.exists();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        sendMsgIB = findViewById(R.id.idIBSend);
        userMsgEdt = findViewById(R.id.idEdtMessage);
        generatedTV = findViewById(R.id.sample_text);
        promptTV = findViewById(R.id.user_text);
        progressText = findViewById(R.id.progress_text);
        settingsButton = findViewById(R.id.idIBSettings);

        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messagesList = new ArrayList<>();
        messagesAdapter = new MessagesAdapter(messagesList);
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messagesAdapter);

        try {
            downloadModels(getApplicationContext());
        } catch (GenAIException e) {
            throw new RuntimeException(e);
        }

        settingsButton.setOnClickListener(v -> {
            BottomSheet bottomSheet = new BottomSheet();
            bottomSheet.setSettingsListener(new BottomSheet.SettingsListener() {
                @Override
                public void onSettingsApplied(int maxLength, float lengthPenalty) {
                    MainActivity.this.maxLength = maxLength;
                    MainActivity.this.lengthPenalty = lengthPenalty;
                    Log.i(TAG, "Setting max response length to: " + maxLength);
                    Log.i(TAG, "Setting length penalty to: " + lengthPenalty);
                }
            });
            bottomSheet.show(getSupportFragmentManager(), "BottomSheet");
        });

        Consumer<String> tokenListener = this;

        generatedTV.setMovementMethod(new ScrollingMovementMethod());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        sendMsgIB.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (tokenizer == null) {
                    Toast.makeText(MainActivity.this, "Model not loaded yet, please wait...", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (userMsgEdt.getText().toString().isEmpty()) {
                    Toast.makeText(MainActivity.this, "Please enter your message..", Toast.LENGTH_SHORT).show();
                    return;
                }

                String promptQuestion = userMsgEdt.getText().toString();
                String promptQuestion_formatted = "<system>You are a helpful AI assistant. Answer in two paragraphs or less<|end|><|user|>"+promptQuestion+"<|end|>\n<assistant|>";
                Log.i("GenAI: prompt question", promptQuestion_formatted);

                messagesList.add(new Message(promptQuestion, MessageType.SENT));
                messagesAdapter.notifyItemInserted(messagesList.size() - 1);
                messagesRecyclerView.scrollToPosition(messagesList.size() - 1);

                userMsgEdt.setText("");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        TokenizerStream stream = null;
                        GeneratorParams generatorParams = null;
                        Generator generator = null;
                        Sequences encodedPrompt = null;
                        try {
                            stream = tokenizer.createStream();

                            generatorParams = model.createGeneratorParams();
                            generatorParams.setSearchOption("length_penalty", lengthPenalty);
                            generatorParams.setSearchOption("max_length", maxLength);

                            encodedPrompt = tokenizer.encode(promptQuestion_formatted);
                            generatorParams.setInput(encodedPrompt);

                            generator = new Generator(model, generatorParams);

                            long startTime = System.currentTimeMillis();
                            long firstTokenTime = startTime;
                            long currentTime = startTime;
                            int numTokens = 0;
                            while (!generator.isDone()) {
                                generator.computeLogits();
                                generator.generateNextToken();

                                int token = generator.getLastTokenInSequence(0);

                                if (numTokens == 0) {
                                    firstTokenTime = System.currentTimeMillis();
                                }

                                tokenListener.accept(stream.decode(token));

                                Log.i(TAG, "Generated token: " + token + ": " + stream.decode(token));
                                Log.i(TAG, "Time taken to generate token: " + (System.currentTimeMillis() - currentTime)/ 1000.0 + " seconds");
                                currentTime = System.currentTimeMillis();
                                numTokens++;
                            }
                            long totalTime = System.currentTimeMillis() - firstTokenTime;

                            float promptProcessingTime = (firstTokenTime - startTime) / 1000.0f;
                            float tokensPerSecond = (1000 * (numTokens - 1)) / totalTime;

                            runOnUiThread(() -> {
                                sendMsgIB.setEnabled(true);
                                sendMsgIB.setAlpha(1.0f);
                                showTokenPopup(promptProcessingTime, tokensPerSecond);
                            });

                            Log.i(TAG, "Prompt processing time (first token): " + promptProcessingTime + " seconds");
                            Log.i(TAG, "Tokens generated per second (excluding prompt processing): " + tokensPerSecond);
                        } catch (GenAIException e) {
                            Log.e(TAG, "Exception occurred during model query: " + e.getMessage());
                        } finally {
                            if (generator != null) generator.close();
                            if (encodedPrompt != null) encodedPrompt.close();
                            if (stream != null) stream.close();
                            if (generatorParams != null) generatorParams.close();
                        }

                        runOnUiThread(() -> {
                            sendMsgIB.setEnabled(true);
                            sendMsgIB.setAlpha(1.0f);
                        });
                    }
                }).start();
            }
        });
    }

    @Override
    protected void onDestroy() {
        tokenizer.close();
        tokenizer = null;
        model.close();
        model = null;
        super.onDestroy();
    }

    private void downloadModels(Context context) throws GenAIException {
        final String baseUrl = "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/";
        List<String> files = Arrays.asList(
                "added_tokens.json",
                "config.json",
                "configuration_phi3.py",
                "genai_config.json",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx",
                "phi3-mini-4k-instruct-cpu-int4-rtn-block-32-acc-level-4.onnx.data",
                "special_tokens_map.json",
                "tokenizer.json",
                "tokenizer.model",
                "tokenizer_config.json");

        List<Pair<String, String>> urlFilePairs = new ArrayList<>();
        for (String file : files) {
            if (!fileExists(context, file)) {
                urlFilePairs.add(new Pair<>(baseUrl + file, file));
            }
        }
        if (urlFilePairs.isEmpty()) {
            Toast.makeText(this, "All files already exist. Skipping download.", Toast.LENGTH_SHORT).show();
            Log.d(TAG, "All files already exist. Skipping download.");
            model = new Model(getFilesDir().getPath());
            tokenizer = model.createTokenizer();
            return;
        }

        progressText.setText("Downloading...");
        progressText.setVisibility(View.VISIBLE);

        Toast.makeText(this, "Downloading model for the app... Model Size greater than 2GB, please allow a few minutes to download.", Toast.LENGTH_SHORT).show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            ModelDownloader.downloadModel(context, urlFilePairs, new ModelDownloader.DownloadCallback() {
                @Override
                public void onProgress(long lastBytesRead, long bytesRead, long bytesTotal) {
                    long lastPctDone = 100 * lastBytesRead / bytesTotal;
                    long pctDone = 100 * bytesRead / bytesTotal;
                    if (pctDone > lastPctDone) {
                        Log.d(TAG, "Downloading files: " + pctDone + "%");
                        runOnUiThread(() -> progressText.setText("Downloading: " + pctDone + "%"));
                    }
                }
                @Override
                public void onDownloadComplete() {
                    Log.d(TAG, "All downloads completed.");
                    try {
                        model = new Model(getFilesDir().getPath());
                        tokenizer = model.createTokenizer();
                        runOnUiThread(() -> {
                            Toast.makeText(context, "All downloads completed", Toast.LENGTH_SHORT).show();
                            progressText.setVisibility(View.INVISIBLE);
                        });
                    } catch (GenAIException e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
            });
        });
        executor.shutdown();
    }

    @Override
    public void accept(String token) {
        runOnUiThread(() -> {
            messagesList.add(new Message(token, MessageType.RECEIVED));
            messagesAdapter.notifyItemInserted(messagesList.size() - 1);
            messagesRecyclerView.scrollToPosition(messagesList.size() - 1);
        });
    }

    public void setVisibility() {
        TextView view = findViewById(R.id.user_text);
        view.setVisibility(View.VISIBLE);
        TextView botView = findViewById(R.id.sample_text);
        botView.setVisibility(View.VISIBLE);
    }

    private void showTokenPopup(float promptProcessingTime, float tokenRate) {
        final Dialog dialog = new Dialog(MainActivity.this);
        dialog.setContentView(R.layout.info_popup);

        TextView promptProcessingTimeTv = dialog.findViewById(R.id.prompt_processing_time_tv);
        TextView tokensPerSecondTv = dialog.findViewById(R.id.tokens_per_second_tv);
        Button closeBtn = dialog.findViewById(R.id.close_btn);

        promptProcessingTimeTv.setText(String.format("Prompt processing time: %.2f seconds", promptProcessingTime));
        tokensPerSecondTv.setText(String.format("Tokens per second: %.2f", tokenRate));

        closeBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
