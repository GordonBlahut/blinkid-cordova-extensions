package com.microblinkextensions.activity;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.Manifest;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog;

import com.microblink.directApi.DirectApiErrorListener;
import com.microblink.directApi.RecognizerRunner;
import com.microblink.entities.recognizers.RecognizerBundle;
import com.microblink.hardware.orientation.Orientation;
import com.microblink.recognition.FeatureNotSupportedException;
import com.microblink.recognition.RecognitionSuccessType;
import com.microblink.view.recognition.ScanResultListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public class DirectApiActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback {

    /** Request code for built-in camera activity. */
    public static final int SELECT_IMAGE_CODE = 1338;
    private static final int PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 23423;

    private static final String TAG = "BlinkIDExtensions";

    private boolean mSelectedImage = false;
    private boolean mRestarted = false;

    /** Bundle that will contain all recognizers that have arrived via Intent */
    private RecognizerBundle mRecognizerBundle = new RecognizerBundle();

    /** RecognizerRunner that will run all recognizers within RecognizerBundle on given image */
    private RecognizerRunner mRecognizerRunner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mRestarted = savedInstanceState != null;

        Log.i(TAG, "DirectApiActivity.onCreate");

        Intent intent = getIntent();
        mRecognizerBundle.loadFromIntent(intent);

        mRecognizerRunner = RecognizerRunner.getSingletonInstance();

        initializeRecognizer();
    }

    @Override
    protected void onStart() {
        super.onStart();

        Log.i(TAG, "DirectApiActivity.onStart");

        if (!mRestarted && isExternalStoragePermissionGranted()) {
            getImage();
        }
    }

    /**
     * Starts built-in gallery for selecting image
     */
    private void getImage() {
      if (!mSelectedImage) {
        Log.i(TAG, "DirectApiActivity.getImage");

        Intent getPictureIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        mSelectedImage = true;

        Log.i(TAG, "DirectApiActivity.getImage starting activity for result");
        startActivityForResult(getPictureIntent, SELECT_IMAGE_CODE);
      }
    }

    private void initializeRecognizer() {
        // initialize recognizer runner singleton
        mRecognizerRunner.initialize(this, mRecognizerBundle, new DirectApiErrorListener() {
            @Override
            public void onRecognizerError(Throwable t) {
                Log.e(TAG, "Failed to initialize recognizer.", t);
                Toast.makeText(DirectApiActivity.this, "Failed to initialize recognizer. Reason: "
                        + t.getMessage(), Toast.LENGTH_LONG).show();
                finish();
                return;
            }
        });
    }

    @SuppressLint("Override")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Log.i(TAG, "DirectApiActivity.onRequestPermissionsResult");

        if (requestCode == PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission Granted
                getImage();
            } else {
                finish();
            }
        }
    }

    private boolean isExternalStoragePermissionGranted() {
        Log.i(TAG, "DirectApiActivity.isExternalStoragePermissionGranted");

        if (ContextCompat.checkSelfPermission(DirectApiActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(DirectApiActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)) {
                new MaterialAlertDialogBuilder(DirectApiActivity.this)
                        .setMessage(getResources().getIdentifier("dialog_storage_permission_read_request_message", "string", getPackageName()))
                        .setPositiveButton(android.R.string.ok, (DialogInterface dialogInterface, int i) -> ActivityCompat.requestPermissions(DirectApiActivity.this, new String[]{ Manifest.permission.READ_EXTERNAL_STORAGE}, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE))
                        .setNegativeButton(android.R.string.cancel, (DialogInterface dialogInterface, int i) -> DirectApiActivity.this.finish())
                        .create()
                        .show();
            } else {
                ActivityCompat.requestPermissions(DirectApiActivity.this, new String[] { Manifest.permission.READ_EXTERNAL_STORAGE }, PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
            }

            return false;
        } else {
            return true;
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        Log.i(TAG, "DirectApiActivity.onStop");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.i(TAG, "DirectApiActivity.onDestroy");

        if (mRecognizerRunner != null) {
            // terminate the native library
            Log.i(TAG, "terminating native library");
            mRecognizerRunner.terminate();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, String.format("DirectApiActivity.onActivityResult, request=%d, result=%d", requestCode, resultCode));

        if (requestCode == SELECT_IMAGE_CODE && resultCode == RESULT_OK) {
            try {
                //Toast.makeText(DirectApiActivity.this, "Scanning selected image.", Toast.LENGTH_LONG).show();

                Log.i(TAG, String.format("RecognizerRunner state = %s", mRecognizerRunner.getCurrentState()));

                if (mRecognizerRunner.getCurrentState() == RecognizerRunner.State.OFFLINE) {
                    initializeRecognizer();
                }

                Log.i(TAG, String.format("RecognizerRunner state = %s", mRecognizerRunner.getCurrentState()));

                Uri imageUri = Objects.requireNonNull(data).getData();
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);

                mRecognizerRunner.recognizeBitmap(bitmap, Orientation.ORIENTATION_LANDSCAPE_RIGHT, new ScanResultListener() {
                    @Override
                    public void onScanningDone(@NonNull RecognitionSuccessType recognitionSuccessType) {
                        Log.i(TAG, String.format("ScanResultListener.onScanningDone, %s", recognitionSuccessType));
                        // if (recognitionSuccessType != RecognitionSuccessType.UNSUCCESSFUL) {
                            // just always return the results and leave it up to the calling up how it wants to handle
                            // a scan with no results
                            Intent intent = new Intent();
                            mRecognizerBundle.saveToIntent(intent);
                            setResult(RESULT_OK, intent);
                        // } else {
                        //     //Toast.makeText(DirectApiActivity.this, "Unable to recognize any documents in the selected image.", Toast.LENGTH_SHORT).show();
                        // }

                        finish();
                        return;
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();

                finish();
                return;
            }
        } else {
            finish();
            return;
        }

        Log.i(TAG,"DirectApiActivity.onActivityResult done");
    }
}
