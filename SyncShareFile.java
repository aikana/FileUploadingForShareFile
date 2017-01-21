package edu.usc.nsg;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.citrix.sharefile.api.SFApiClient;
import com.citrix.sharefile.api.SFSdk;
import com.citrix.sharefile.api.authentication.SFOAuth2Token;
import com.citrix.sharefile.api.authentication.SFOAuthService;
import com.citrix.sharefile.api.exceptions.SFSDKException;
import com.citrix.sharefile.api.https.SFUploadRunnable;
import com.citrix.sharefile.api.https.TransferRunnable;
import com.citrix.sharefile.api.interfaces.ISFApiClient;
import com.citrix.sharefile.api.models.SFFolder;
import com.citrix.sharefile.api.models.SFItem;
import com.citrix.sharefile.api.models.SFODataFeed;
import com.citrix.sharefile.api.models.SFSession;
import com.citrix.sharefile.api.models.SFUploadRequestParams;

/**
 * Copyright (c) 2014 Citrix Systems, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a
 * copy of this software and associated documentation files (the "Software"),
 * to deal in the Software without restriction, including without limitation
 * the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 * IN THE SOFTWARE.
 */

/**
 * The functions in this file will make use of the ShareFile API v3 to show some
 * of the basic operations using GET, POST, PATCH, DELETE HTTP verbs. See
 * api.sharefile.com for more information.
 *
 *
 * Requirements:
 *
 * 1. JSON library - google gson, https://code.google.com/p/google-gson/
 *
 * Authentication:
 *
 * OAuth2 password grant is used for authentication. After the token is acquired
 * it is sent an an authorization header with subsequent API requests.
 *
 * Exception / Error Checking:
 *
 * For simplicity, exception handling has not been added. Code should not be
 * used in a production environment.
 */

public class SyncShareFile {


	public static void searchDir(File dir, String extension, List<String> list) {
		String pattern = extension; // for example ".java"
		File listFile[] = dir.listFiles();
		if (listFile != null) {
			for (int i = 0; i < listFile.length; i++) {
				if (listFile[i].isDirectory()) {
					searchDir(listFile[i], extension, list);
				} else {
					// System.out.println(listFile[i].getPath() +
					// listFile[i].getName());
					// search files based on file extension.
					if (listFile[i].getName().endsWith(pattern)) {
						list.add(listFile[i].getPath());
					}
				}
			}
		}
	}

	public static void cutFrontPath(List<String> list, String front, List<String> cutlist) {
		for (int i = 0; i < list.size(); i++) {
			cutlist.add(i, list.get(i).substring(front.length(), list.get(i).length()));
		}
	}

	public static void generatingFolderInRecursive(ISFApiClient apiClient, URI url, String path) {
		try {
			// base directory list get
			SFODataFeed<SFItem> folderContents = apiClient.items().getChildren(url).execute();
			ArrayList<SFItem> children = folderContents.getFeed();
			String currentLevelFolder = "";
			String remainingPath = "";
			
			if (path.indexOf("/") == 0)
				path = path.substring(1);

			if (path.indexOf("/") > 0) {
				currentLevelFolder = path.substring(0, path.indexOf("/"));
				remainingPath = path.substring(path.indexOf("/") + 1);
				for (int i = 0; i < children.size(); i++) {
					SFItem temp = children.get(i);
					if (temp.get__type().compareToIgnoreCase("ShareFile.Api.Models.Folder") == 0) {
						if (temp.getName().compareToIgnoreCase(currentLevelFolder) == 0) {
							// System.out.printf("%s path : %s\n",
							// temp.geturl(), remainingPath);
							generatingFolderInRecursive(apiClient, temp.geturl(), remainingPath);
							return;
						}
					}
				}
				// if there is not matched folder
				SFFolder newFolder = new SFFolder();
				newFolder.setName(currentLevelFolder);
				//System.out.println("path: " + path +" url : "+ url.toString() + " newFolder Name :"+newFolder.getName());
				SFFolder saved = apiClient.items().createFolder(url, newFolder).execute();
				generatingFolderInRecursive(apiClient, saved.geturl(), remainingPath);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void makeAllPathinShareFile(ISFApiClient apiClient, URI url, List<String> dirlist) {
		// base directory list get
		for (int i = 0; i < dirlist.size(); i++) {
			String path = dirlist.get(i);
			generatingFolderInRecursive(apiClient, url, path);
		}
	}

	public static URI findFolderIDbyPath(ISFApiClient apiClient, URI url, String path) {
		try {
			// base directory list get
			SFODataFeed<SFItem> folderContents = apiClient.items().getChildren(url).execute();
			ArrayList<SFItem> children = folderContents.getFeed();
			String currentLevelFolder = "";
			String remainingPath = "";
			URI id = url;
			if (path.indexOf("/") == 0)
				path = path.substring(1);
			//System.out.println(path);

			if (path.indexOf("/") > 0) {
				currentLevelFolder = path.substring(0, path.indexOf("/"));
				remainingPath = path.substring(path.indexOf("/") + 1);
				for (int i = 0; i < children.size(); i++) {
					SFItem temp = children.get(i);
					if (temp.get__type().compareToIgnoreCase("ShareFile.Api.Models.Folder") == 0) {
						//System.out.println("find folder :" + temp.getName());
						if (temp.getName().compareToIgnoreCase(currentLevelFolder) == 0) {
							//System.out.println("current folder Name :" + currentLevelFolder);
							id = findFolderIDbyPath(apiClient, temp.geturl(), remainingPath);
						}
					}
				}
				// this should not be current url if you run
				// makeAllPathinShareFile before and path has subfolder.
				return id;
			} else {
				return url;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return url;
	}

	public static void transferAllFilesToShareFile(ISFApiClient apiClient, URI url, List<String> dirlist, List<String> cutlist) {
		TransferRunnable.IProgress progressListener[] = new TransferRunnable.IProgress[dirlist.size()];

		for (int i = 0; i < dirlist.size(); i++) {
			
			progressListener[i] = new TransferRunnable.IProgress() {
				@Override
				public void bytesTransfered(long bytesTrasnfered) {
					//System.out.println(this.getClass().getName()+ " bytes: " + bytesTrasnfered);
				}

				@Override
				public void onError(SFSDKException e, long bytesTrasnfered) {
				}

				@Override
				public void onComplete(long bytesTrasnfered) {
					checkUploadedFiles(apiClient, url, dirlist, cutlist);
					System.out.println(bytesTrasnfered + "bytes Transfer End");
				}
			};

			String path = cutlist.get(i);
			String fullpath = dirlist.get(i);
			URI target_url = findFolderIDbyPath(apiClient, url, path);
			 
			 // file location setting.
			try {
				FileInputStream inputStream = new FileInputStream(fullpath);
				SFUploadRequestParams requestParams = new SFUploadRequestParams();
				
				requestParams.setFileName(path.substring(path.lastIndexOf("/")+1));
				requestParams.setDetails("details"); // detail information will be generated 
				requestParams.setFileSize((long)inputStream.available()); requestParams.seturl(target_url);
				
				SFUploadRunnable uploader = apiClient.getUploader(requestParams,inputStream, progressListener[i]);
				
				uploader.start(); // this is async by default
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public static void checkUploadedFiles(ISFApiClient apiClient,URI url,List<String> dirlist,List<String> cutlist)
	{
		try {
			for (int i = 0; i < dirlist.size(); i++) {
				String path = cutlist.get(i);
				String fullpath = dirlist.get(i);
				URI target_url = findFolderIDbyPath(apiClient, url, path);
				
				SFODataFeed<SFItem> folderContents = apiClient.items().getChildren(target_url).execute();
				ArrayList<SFItem> children = folderContents.getFeed();
				int ind = path.lastIndexOf("/");
				String currentFileName ="";
				if (ind >= 0) 
					currentFileName = path.substring(ind+1);
				else
					currentFileName = path;
				
				for (int j = 0; j < children.size(); j++) {
					SFItem temp = children.get(j);
					if (temp.get__type().compareToIgnoreCase("ShareFile.Api.Models.File") == 0) {
						if (temp.getName().compareToIgnoreCase(currentFileName) == 0) {
							File local = new File(dirlist.get(i));
							if (local.length() == temp.getFileSizeBytes()) {
								System.out.println(temp.getName() + " path: " + fullpath + " is transfered completely");
								continue;
							}
							else {
								System.out.println(temp.getName() + " path:" + fullpath + " is needed to be transfered again");
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}	

	public static void main(String[] args) throws Exception {
		String hostname = "universityofsoutherncalifornia.sharefile.com";
		String username = "XXXX@usc.edu";
		String password = "XXXXXXXXXXXXXX";
		String clientId = "-------------- your id-----";
		String clientSecret = "----- your secret ----------";

		SFSdk.init(clientId, clientSecret, hostname);
		SFOAuthService oAuthService = new SFOAuthService();
		SFOAuth2Token authToken = oAuthService.authenticate("universityofsoutherncalifornia", "sharefile.com", username,
				password);
		ISFApiClient apiClient = new SFApiClient(authToken);

		SFSession session = apiClient.sessions().login().execute();

		// get file list from folder
		SFFolder folder = (SFFolder) apiClient.items().get().execute();

		String searchspace = "/home/zaizhen/homer/";
		List<String> dirlist = new LinkedList<String>();
		List<String> cutdirlist = new LinkedList<String>();
		searchDir(new File(searchspace), ".gz", dirlist);
		cutFrontPath(dirlist, searchspace, cutdirlist);

		makeAllPathinShareFile(apiClient, folder.geturl(), cutdirlist);
		transferAllFilesToShareFile(apiClient, folder.geturl(), dirlist, cutdirlist);
		
		
		//checkUploadedFiles(apiClient, folder.geturl(), dirlist, cutdirlist);

		for (int i = 0; i < dirlist.size(); i++) {
			System.out.println(dirlist.get(i));
		}
	}

}