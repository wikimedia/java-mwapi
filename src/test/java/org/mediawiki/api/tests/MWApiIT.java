package org.mediawiki.api.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import in.yuvi.http.fluent.ProgressListener;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

import org.apache.http.impl.client.DefaultHttpClient;
import org.junit.Before;
import org.junit.Test;
import org.mediawiki.api.ApiResult;
import org.mediawiki.api.MWApi;

public class MWApiIT {

    // Test accounts on local wiki. For write tests
    // Setup your local wiki and create this account before running tests
    private final String USERNAME = "yuvipanda";
    private final String PASSWORD = "plasmafury";
    private final String WRITEAPIURL = "http://localhost/w/api.php";

    // Use testwiki for read only tests
    private final String READAPIURL = "http://localhost/w/api.php";

    private MWApi api;

    @Before
    public void setUp() {
        api = new MWApi(READAPIURL, new DefaultHttpClient());
    }

    private void setupWriteableAPI() {
        api = new MWApi(WRITEAPIURL, new DefaultHttpClient());
    }

    @Test
    public void testLogin() throws IOException {
        setupWriteableAPI();
        assertEquals("Success", api.login(USERNAME, PASSWORD));
    }

    @Test
    public void testLogout() throws IOException {
        setupWriteableAPI();
        // Login, check token, logout, check token
        assertEquals("Success", api.login(USERNAME, PASSWORD));
        assertFalse("+\\".equals(api.getEditToken()));
        api.logout();
        assertEquals("+\\", api.getEditToken());
    }
   
    // <Insert profanity about Java>
    private String sha1Of(String filepath) throws IOException {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA1");
        } catch (NoSuchAlgorithmException e) {
            // And I'm batman. Fuck you, Java
            throw new RuntimeException(e);
        }
        FileInputStream fis = new FileInputStream(filepath);
        try {
          byte[] dataBytes = new byte[1024];
       
          int nread = 0; 
       
          while ((nread = fis.read(dataBytes)) != -1) {
            md.update(dataBytes, 0, nread);
          };
       
          byte[] mdbytes = md.digest();
       
          //convert the byte to hex format
          StringBuffer sb = new StringBuffer("");
          for (int i = 0; i < mdbytes.length; i++) {
              sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
          }
          
          return sb.toString();
        } finally {
          fis.close();
        }
    }
    
    @Test
    public void testUpload() throws IOException {
        setupWriteableAPI();
        
        String filepath = Thread.currentThread().getContextClassLoader().getResource("test.png").getFile();
        assertEquals("Success", api.login(USERNAME, PASSWORD));
        FileInputStream stream = new FileInputStream(filepath);
        ApiResult result = api.upload("test", stream, "yo!", "Wassup?");
        assertEquals("Success", result.getString("/api/upload/@result"));
        assertEquals(sha1Of(filepath), result.getString("/api/upload/imageinfo/@sha1"));
    }
   
    private class ArrayListOutputProgressListener implements ProgressListener {

        ArrayList<Double> list;
        
        public ArrayListOutputProgressListener(ArrayList<Double> list) {
           this.list = list; 
        }

        @Override
        public void onProgress(long transferred, long total) {
           list.add((double) transferred/ (double) total); 
        }
    }
    
    private long countBytes(InputStream source) throws IOException {
        long length = 0;
        while(source.read() != -1) {
            length++;
        }
        return length;
    }
    
    @Test
    public void testUploadWithProgress() throws IOException {
        setupWriteableAPI();

        String filepath = Thread.currentThread().getContextClassLoader().getResource("test.png").getFile();

        assertEquals("Success", api.login(USERNAME, PASSWORD));
        FileInputStream stream = new FileInputStream(filepath);
        FileInputStream streamForCounting = new FileInputStream(filepath);
        long length = countBytes(streamForCounting);
        
        ArrayList<Double> progressValues = new ArrayList<Double>();
        ApiResult result = api.upload("test", stream, length, "yo!", "Wassup?", false, new ArrayListOutputProgressListener(progressValues));
        assertEquals(sha1Of(filepath), result.getString("/api/upload/imageinfo/@sha1"));
        
        // TODO: Very simple check, do something a lot more complete
        assertNotSame(0, progressValues.size());
        assertEquals(1.0, progressValues.get(progressValues.size() - 1).doubleValue(), 0.0);
       
        Double lastValue = progressValues.get(0);
        for(Double d : progressValues) {
           assertTrue(d >= lastValue); 
        }
    }

    @Test
    public void testAuthCookieLogin() throws IOException {
        setupWriteableAPI();
        assertEquals("Success", api.login(USERNAME, PASSWORD));
        String authCookie = api.getAuthCookie();
        assertNotNull(authCookie);
        // reset API
        setupWriteableAPI();
        assertFalse(api.validateLogin());
        assertEquals("+\\", api.getEditToken());
        api.setAuthCookie(authCookie);
        assertEquals(authCookie, api.getAuthCookie());
        assertTrue(api.validateLogin());
        assertFalse("+\\".equals(api.getEditToken()));
    }
    
    @Test
    public void testValidateLogin() throws IOException {
        setupWriteableAPI();
        assertFalse(api.validateLogin());
        assertEquals("Success", api.login(USERNAME, PASSWORD));
        assertTrue(api.validateLogin());
    }
    
    @Test
    public void testLoggedInEditAttempt() throws IOException {
        setupWriteableAPI();
        assertEquals("Success", api.login(USERNAME, PASSWORD));
        String token = api.getEditToken();
        String text = "Has anyone really been far even as decided to use even go want to do look more like?";
        String title = "India";
        ApiResult editResult = api.action("edit").param("title", title).param("text", text).param("token", token).param("summary", "Sample summary").post();
        assertEquals("Success", editResult.getString("/api/edit/@result"));
        ApiResult checkResult = api.action("parse").param("page", title).param("prop", "wikitext").get();
        assertEquals(text, checkResult.getString("/api/parse/wikitext"));
    }

    @Test
    public void testAnonymousEditToken() throws IOException {
        // +\ is anonymous edit token
        assertEquals("+\\", api.getEditToken());
    }

}
