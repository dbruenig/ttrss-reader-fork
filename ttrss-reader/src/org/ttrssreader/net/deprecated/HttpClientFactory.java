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

package org.ttrssreader.net.deprecated;

import java.security.KeyStore;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.SocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.HttpParams;
import org.ttrssreader.controllers.Controller;
import org.ttrssreader.utils.SSLUtils;
import android.util.Log;

/**
 * Create a HttpClient object based on the user preferences.
 */
public class HttpClientFactory {
    
    protected static final String TAG = HttpClientFactory.class.getSimpleName();
    
    private static HttpClientFactory instance;
    private SchemeRegistry registry;
    
    public HttpClientFactory() {
        
        boolean trustAllSslCerts = Controller.getInstance().trustAllSsl();
        boolean useCustomKeyStore = Controller.getInstance().useKeystore();
        
        registry = new SchemeRegistry();
        registry.register(new Scheme("http", new PlainSocketFactory(), 80));
        
        SocketFactory socketFactory = null;
        
        if (useCustomKeyStore && !trustAllSslCerts) {
            String keystorePassword = Controller.getInstance().getKeystorePassword();
            
            socketFactory = newSslSocketFactory(keystorePassword);
            if (socketFactory == null) {
                socketFactory = SSLSocketFactory.getSocketFactory();
                Log.w(TAG, "Custom key store could not be read, using default settings.");
            }
            
        } else if (trustAllSslCerts) {
            socketFactory = new FakeSocketFactory();
        } else {
            socketFactory = SSLSocketFactory.getSocketFactory();
        }
        
        registry.register(new Scheme("https", socketFactory, 443));
        
    }
    
    public DefaultHttpClient getHttpClient(HttpParams httpParams) {
        DefaultHttpClient httpInstance = new DefaultHttpClient(new ThreadSafeClientConnManager(httpParams, registry),
                httpParams);
        return httpInstance;
    }
    
    public static HttpClientFactory getInstance() {
        synchronized (HttpClientFactory.class) {
            if (instance == null) {
                instance = new HttpClientFactory();
            }
        }
        return instance;
    }
    
    /**
     * Create a socket factory with the custom key store
     * 
     * @param keystorePassword
     *            the password to unlock the custom keystore
     * 
     * @return socket factory with custom key store
     */
    private static SSLSocketFactory newSslSocketFactory(String keystorePassword) {
        try {
            KeyStore keystore = SSLUtils.loadKeystore(keystorePassword);
            
            return new SSLSocketFactory(keystore);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }
    
}