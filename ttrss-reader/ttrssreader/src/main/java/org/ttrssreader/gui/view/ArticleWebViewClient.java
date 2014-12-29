/*
 * ttrss-reader-fork for Android
 * 
 * Copyright (C) 2010 Nils Braden
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

package org.ttrssreader.gui.view;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;
import org.ttrssreader.R;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.gui.MediaPlayerActivity;
import org.ttrssreader.preferences.Constants;
import org.ttrssreader.utils.AsyncTask;
import org.ttrssreader.utils.FileUtils;
import org.ttrssreader.utils.Utils;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.webkit.URLUtil;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ArticleWebViewClient extends WebViewClient {
    
    private static final String TAG = ArticleWebViewClient.class.getSimpleName();
    
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, final String url) {
        
        final Context context = view.getContext();
        
        boolean audioOrVideo = false;
        for (String s : FileUtils.AUDIO_EXTENSIONS) {
            if (url.toLowerCase(Locale.getDefault()).contains("." + s)) {
                audioOrVideo = true;
                break;
            }
        }
        
        for (String s : FileUtils.VIDEO_EXTENSIONS) {
            if (url.toLowerCase(Locale.getDefault()).contains("." + s)) {
                audioOrVideo = true;
                break;
            }
        }
        
        if (audioOrVideo) {
            // @formatter:off
            final CharSequence[] items = {
                    (String) context.getText(R.string.WebViewClientActivity_Display),
                    (String) context.getText(R.string.WebViewClientActivity_Download) };
            // @formatter:on
            
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("What shall we do?");
            builder.setItems(items, new DialogInterface.OnClickListener() {
                
                public void onClick(DialogInterface dialog, int item) {
                    
                    switch (item) {
                        case 0:
                            Log.i(TAG, "Displaying file in mediaplayer: " + url);
                            Intent i = new Intent(context, MediaPlayerActivity.class);
                            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            i.putExtra(MediaPlayerActivity.URL, url);
                            context.startActivity(i);
                            break;
                        case 1:
                            try {
                                new AsyncMediaDownloader(context).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,
                                        new URL(url));
                            } catch (MalformedURLException e) {
                                e.printStackTrace();
                            }
                            break;
                        default:
                            Log.e(TAG, "Doing nothing, but why is that?? Item: " + item);
                            break;
                    }
                }
            });
            AlertDialog alert = builder.create();
            alert.show();
        } else {
            Uri uri = Uri.parse(url);
            
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        return true;
    }
    
    private boolean externalStorageState() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        } else {
            return false;
        }
    }
    
    private class AsyncMediaDownloader extends AsyncTask<URL, Void, Void> {
        private final static int BUFFER = (int) Utils.KB;
        
        private WeakReference<Context> contextRef;
        
        public AsyncMediaDownloader(Context context) {
            this.contextRef = new WeakReference<Context>(context);
        }
        
        protected Void doInBackground(URL... urls) {
            
            if (urls.length < 1) {
                String msg = "No URL given, skipping download...";
                Log.w(TAG, msg);
                Utils.showFinishedNotification(msg, 0, true, contextRef.get());
                return null;
            } else if (!externalStorageState()) {
                String msg = "External Storage not available, skipping download...";
                Log.w(TAG, msg);
                Utils.showFinishedNotification(msg, 0, true, contextRef.get());
                return null;
            }
            
            long start = System.currentTimeMillis();
            Utils.showRunningNotification(contextRef.get(), false);
            
            // Use configured output directory
            File folder = new File(Controller.getInstance().saveAttachmentPath());
            if (!folder.exists() && !folder.mkdirs()) {
                // Folder could not be created, fallback to internal directory on sdcard
                folder = new File(Constants.SAVE_ATTACHMENT_DEFAULT);
                folder.mkdirs();
            }
            
            if (!folder.exists())
                folder.mkdirs();
            
            BufferedInputStream in = null;
            FileOutputStream fos = null;
            BufferedOutputStream bout = null;
            
            int size = -1;
            
            File file = null;
            try {
                URL url = urls[0];
                HttpURLConnection c = (HttpURLConnection) url.openConnection();
                
                file = new File(folder, URLUtil.guessFileName(url.toString(), null, ".mp3"));
                if (file.exists()) {
                    size = (int) file.length();
                    c.setRequestProperty("Range", "bytes=" + size + "-"); // try to resume downloads
                }
                
                c.setRequestMethod("GET");
                c.setDoInput(true);
                c.setDoOutput(true);
                
                in = new BufferedInputStream(c.getInputStream());
                fos = (size == 0) ? new FileOutputStream(file) : new FileOutputStream(file, true);
                bout = new BufferedOutputStream(fos, BUFFER);
                
                byte[] data = new byte[BUFFER];
                int count = 0;
                while ((count = in.read(data, 0, BUFFER)) >= 0) {
                    bout.write(data, 0, count);
                    size += count;
                }
                
                int time = (int) ((System.currentTimeMillis() - start) / Utils.SECOND);
                
                // Show Intent which opens the file
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                if (file != null)
                    intent.setDataAndType(Uri.fromFile(file), FileUtils.getMimeType(file.getName()));
                
                Log.i(TAG, "Finished. Path: " + file.getAbsolutePath() + " Time: " + time + "s Bytes: " + size);
                Utils.showFinishedNotification(file.getAbsolutePath(), time, false, contextRef.get(), intent);
                
            } catch (IOException e) {
                String msg = "Error while downloading: " + e;
                Log.e(TAG, msg, e);
                Utils.showFinishedNotification(msg, 0, true, contextRef.get());
            } finally {
                // Remove "running"-notification
                Utils.showRunningNotification(contextRef.get(), true);
                if (bout != null) {
                    try {
                        bout.close();
                    } catch (IOException e) {
                    }
                }
            }
            return null;
        }
    }
    
}