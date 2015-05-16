/*
The MIT License (MIT)

Copyright (c) 2013 pwlin - pwlin05@gmail.com

Permission is hereby granted, free of charge, to any person obtaining a copy of
this software and associated documentation files (the "Software"), to deal in
the Software without restriction, including without limitation the rights to
use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
the Software, and to permit persons to whom the Software is furnished to do so,
subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/
package io.github.pwlin.cordova.plugins.fileopener2;

import java.io.File;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
//import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaResourceApi;

//import for saveFile
import android.os.Environment;
import android.content.res.AssetManager;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileOpener2 extends CordovaPlugin {

	/**
	 * Executes the request and returns a boolean.
	 * 
	 * @param action
	 *            The action to execute.
	 * @param args
	 *            JSONArry of arguments for the plugin.
	 * @param callbackContext
	 *            The callback context used when calling back into JavaScript.
	 * @return boolean.
	 */
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("open")) {
			this._open(args.getString(0), args.getString(1), callbackContext);
		} 
		else if (action.equals("uninstall")) {
			this._uninstall(args.getString(0), callbackContext);
		}
		else if (action.equals("appIsInstalled")) {
			JSONObject successObj = new JSONObject();
			if (this._appIsInstalled(args.getString(0))) {
				successObj.put("status", PluginResult.Status.OK.ordinal());
				successObj.put("message", "Installed");
			}
			else {
				successObj.put("status", PluginResult.Status.NO_RESULT.ordinal());
				successObj.put("message", "Not installed");
			}
			callbackContext.success(successObj);
		}
		else {
			JSONObject errorObj = new JSONObject();
			errorObj.put("status", PluginResult.Status.INVALID_ACTION.ordinal());
			errorObj.put("message", "Invalid action");
			callbackContext.error(errorObj);
		}
		return true;
	}

	private void _open(String fileArg, String contentType, CallbackContext callbackContext) throws JSONException {
		String fileName = "";
		try {
			CordovaResourceApi resourceApi = webView.getResourceApi();
			Uri fileUri = resourceApi.remapUri(Uri.parse(fileArg));
			fileName = this.stripFileProtocol(fileUri.toString());
		} catch (Exception e) {
			fileName = fileArg;
		}
		File file = saveFile(fileName);
    	System.out.println("PdfViewer: loading url "+file);
		
		if (file.exists()) {
			try {
				Uri path = Uri.fromFile(file);
				Intent intent = new Intent(Intent.ACTION_VIEW);
				intent.setDataAndType(path, contentType);
				intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				/*
				 * @see
				 * http://stackoverflow.com/questions/14321376/open-an-activity-from-a-cordovaplugin
				 */
				cordova.getActivity().startActivity(intent);
				//cordova.getActivity().startActivity(Intent.createChooser(intent,"Open File in..."));
				callbackContext.success();
			} catch (android.content.ActivityNotFoundException e) {
				JSONObject errorObj = new JSONObject();
				errorObj.put("status", PluginResult.Status.ERROR.ordinal());
				errorObj.put("message", "Activity not found: " + e.getMessage());
				callbackContext.error(errorObj);
			}
		} else {
			JSONObject errorObj = new JSONObject();
			errorObj.put("status", PluginResult.Status.ERROR.ordinal());
			errorObj.put("message", "File not found");
			callbackContext.error(errorObj);
		}
	}
	
	private void _uninstall(String packageId, CallbackContext callbackContext) throws JSONException {
		if (this._appIsInstalled(packageId)) {
			Intent intent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE);
			intent.setData(Uri.parse("package:" + packageId));
			cordova.getActivity().startActivity(intent);
			callbackContext.success();
		}
		else {
			JSONObject errorObj = new JSONObject();
			errorObj.put("status", PluginResult.Status.ERROR.ordinal());
			errorObj.put("message", "This package is not installed");
			callbackContext.error(errorObj);
		}
	}
	
	private boolean _appIsInstalled(String packageId) {
		PackageManager pm = cordova.getActivity().getPackageManager();
        boolean appInstalled = false;
        try {
            pm.getPackageInfo(packageId, PackageManager.GET_ACTIVITIES);
            appInstalled = true;
        } catch (PackageManager.NameNotFoundException e) {
            appInstalled = false;
        }
        return appInstalled;
	}

	private String stripFileProtocol(String uriString) {
		if (uriString.startsWith("file://")) {
			uriString = uriString.substring(7);
		}
		return uriString;
	}

    public File saveFile(String fileName){
    	File file = null;    	

    	boolean mExternalStorageAvailable = false;
    	boolean mExternalStorageWriteable = false;
    	String state = Environment.getExternalStorageState();

    	if (Environment.MEDIA_MOUNTED.equals(state)) {
    	    // We can read and write the media
    	    mExternalStorageAvailable = mExternalStorageWriteable = true;
    	} else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
    	    // We can only read the media
    	    mExternalStorageAvailable = true;
    	    mExternalStorageWriteable = false;
    	} else {
    	    // Something else is wrong. It may be one of many other states, but all we need
    	    //  to know is we can neither read nor write
    	    mExternalStorageAvailable = mExternalStorageWriteable = false;
    	}

    	String appDir = "/Android/data/com.maasland.catalog/files/";

    	AssetManager assetManager = cordova.getActivity().getAssets();

    	try {
    		//using a buffer size of 1024 is IMHO a bad idea. In a Smartphone all media typically have a block-size of 4096 bytes or larger (e.g. SD-Card)
    		int bufferSize = 4096;

//	    	String[] as =  assetManager.list("www"+File.separator+"pdf");
//	    	for (int i = 0; i < as.length; i++) {
//	    		System.out.println("="+as[i]);
//			}

	    	System.out.println("AssetManager: open:"+fileName);

	        // Data exceeds UNCOMPRESS_DATA_MAX (5278796 vs 4194304)
	    	// make a commpressed file, to prevent this error on pre Android 2.3.3
	    	// http://stackoverflow.com/questions/5789177/i-get-this-error-data-exceeds-uncompress-data-max-on-android-2-2-but-not-on-2-3
	    	file = new File(cordova.getActivity().getExternalFilesDir(null), "maasland_temp.pdf");
	    	if (false) //(file.exists() && file.length() != 0)
	    	{
	    		System.out.println("File already exists");
	    	} else {	    			    	
	            // Very simple code to copy a picture from the application's
	            // resource into the external file.  Note that this code does
	            // no error checking, and assumes the picture is small (does not
	            // try to copy it in chunks).  Note that if external storage is
	            // not currently mounted this will silently fail.
	        	InputStream is = assetManager.open(fileName);
	            //OutputStream os = new FileOutputStream(file);

	            //OutputStream os = new BufferedOutputStream(new FileOutputStream(file));

	        	//Default buffer size used in BufferedInputStream constructor. It would be better to be explicit if an 8k buffer is required.
	            BufferedInputStream bIS = new BufferedInputStream(is, 8192);

//	            byte[] buffer = new byte[bIS.available()];
	            byte[] buffer = new byte[bufferSize];
	            FileOutputStream fOS = new FileOutputStream(file);
	            int bufferLength = 0;
	            while ((bufferLength = bIS.read(buffer)) > 0) {
	            	fOS.write(buffer, 0, bufferLength);
	            }


//	            byte data[]=new byte[1024];
//	            int len;
//	            while((len=is.read(data))>0)
//	              os.write(data,0,len);

	            //byte[] data = new byte[is.available()];

	            //is.read(data);
	            //fOS.write(data);
	            is.close();
	            fOS.close();		        
	    	}	       	        
    	} catch (IOException e) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
    		// nee, uncompressed file is te groot
    		System.out.println("Error " + e.getMessage());

    		//((DroidGap) cordova.getActivity()).displayError("Er is een fout opgetreden", "De pdf kan niet worden geopend" , "Ok", false);

    		return null;
        } 
    	return file;
    }
}
