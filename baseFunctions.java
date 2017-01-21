package edu.usc.nsg;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.http.client.ClientProtocolException;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class baseFunctions {
	/**
	 * Convenience class to wrap up the Json returned from the ShareFile OAuth2
	 * authentication endpoint
	 *
	 */
	static class OAuth2Token {
		public String accessToken = "";
		public String refreshToken = "";
		public String tokenType = "";
		public String appcp = "";
		public String apicp = "";
		public String subdomain = "";
		public int expiresIn = 0;

		public OAuth2Token(JsonObject json) {
			if (json != null) {
				accessToken = json.get("access_token").getAsString();
				refreshToken = json.get("refresh_token").getAsString();
				tokenType = json.get("token_type").getAsString();
				appcp = json.get("appcp").getAsString();
				apicp = json.get("apicp").getAsString();
				subdomain = json.get("subdomain").getAsString();
				expiresIn = json.get("expires_in").getAsInt();
			}
		}
	}

	/**
	 * Authenticate via username/password
	 *
	 * @param hostname
	 *            - hostname like "myaccount.sharefile.com"
	 * @param clientId
	 *            - your client id
	 * @param clientSecret
	 *            - your client secret
	 * @param username
	 *            - my@user.name
	 * @param password
	 *            - mypassword
	 * @return an OAuth2Token instance
	 */
	public static OAuth2Token authenticate(String hostname, String clientId, String clientSecret, String username,
			String password) throws IOException {
		URL grantUrl = new URL(String.format("https://%s/oauth/token", hostname));

		HashMap<String, String> params = new HashMap<String, String>();
		StringBuilder queryString = new StringBuilder();

		params.put("grant_type", "password");
		params.put("client_id", clientId);
		params.put("client_secret", clientSecret);
		params.put("username", username);
		params.put("password", password);
		for (Map.Entry<String, String> entry : params.entrySet()) {
			queryString.append(
					String.format("%s=%s&", entry.getKey(), URLEncoder.encode(entry.getValue().toString(), "UTF-8")));
		}
		queryString.deleteCharAt(queryString.length() - 1);
		System.out.println(queryString);

		HttpURLConnection connection = (HttpURLConnection) grantUrl.openConnection();
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		connection.setDoOutput(true);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		connection.getOutputStream().write(queryString.toString().getBytes());

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		// print http response code/message and response body
		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);

		JsonObject token = null;
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			token = (JsonObject) parser.parse(response.toString());
		}
		// OAuth2Token oauth2Token = new OAuth2Token(token);

		return new OAuth2Token(token);
	}

	public static void addAuthorizationHeader(HttpURLConnection connection, OAuth2Token token) {
		connection.addRequestProperty("Authorization", String.format("Bearer %s", token.accessToken));
	}

	public static String getHostname(OAuth2Token token) {
		return String.format("%s.sf-api.com", token.subdomain);
	}

	/**
	 * Get the root level Item for the provided user. To retrieve Children the
	 * $expand=Children parameter can be added.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param getChildren
	 *            - retrieve Children Items if True
	 * @throws ClientProtocolException
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public static void getRoot(OAuth2Token token, boolean getChildren) throws IOException, ParseException {
		String urlstr = String.format("https://%s/sf/v3/Items", baseFunctions.getHostname(token));
		if (getChildren) {
			urlstr += "?$expand=Children";
		}
		URL url = new URL(urlstr);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		// print http response code/message and response body
		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			JsonObject items = (JsonObject) parser.parse(response.toString());

			// print the Id, CreationDate, Name for the root Item
			System.out.println(items.get("Id").getAsString() + " | "
					+ new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.S").parse(items.get("CreationDate").getAsString())
					+ " | " + items.get("Name").getAsString());

			// print the Id, CreationDate, Name for any Children
			JsonElement eChildren = items.get("Children");
			if (eChildren != null) {
				JsonArray children = eChildren.getAsJsonArray();
				for (JsonElement element : children) {
					JsonObject child = element.getAsJsonObject();
					System.out
							.println(child.get("Id").getAsString() + " | "
									+ new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.S")
											.parse(child.get("CreationDate").getAsString())
									+ " | " + child.get("Name").getAsString());
				}
			}
		}

		connection.disconnect();
	}

	/**
	 * Gets a single Item by Id.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param id
	 *            - an Item Id
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public static void getItemById(OAuth2Token token, String id) throws IOException, ParseException {
		URL url = new URL(String.format("https://%s/sf/v3/Items(%s)", baseFunctions.getHostname(token), id));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			JsonObject items = (JsonObject) parser.parse(response.toString());

			// print the Id, CreationDate, Name for the root Item
			System.out.println(items.get("Id").getAsString() + " | "
					+ new SimpleDateFormat("yyyy-MM-dd'T'kk:mm:ss.S").parse(items.get("CreationDate").getAsString())
					+ " | " + items.get("Name").getAsString());
		}

		connection.disconnect();
	}

	/**
	 * Get a folder using some of the common query parameters that are
	 * available. This will add the expand, select parameters. The following are
	 * used: expand=Children to get any Children of the folder
	 * select=Id,Name,Children/Id,Children/Name to get the Id, Name of the
	 * folder and the Id, Name of any Children
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param folderId
	 *            - a Folder Id
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public static void getFolderWithQueryParameters(OAuth2Token token, String folderId)
			throws IOException, ParseException {
		URL url = new URL(
				String.format("https://%s/sf/v3/Items(%s)?$expand=Children&$select=Id,Name,Children/Id,Children/Name",
						baseFunctions.getHostname(token), folderId));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		// print http response code/message and response body
		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			JsonObject items = (JsonObject) parser.parse(response.toString());

			// The only Item properties that are available are Id, Name since
			// that is what we selected in the URI
			System.out.println(items.get("Id").getAsString() + " | " + items.get("Name").getAsString());

			// The only Item properties that are available are Id, Name since
			// that is what we selected in the URI
			JsonElement eChildren = items.get("Children");
			if (eChildren != null) {
				JsonArray children = eChildren.getAsJsonArray();
				for (JsonElement element : children) {
					JsonObject child = element.getAsJsonObject();
					System.out.println(child.get("Id").getAsString() + " | " + child.get("Name").getAsString());
				}
			}
		}

		connection.disconnect();
	}

	/**
	 * Create a new folder in the given parent folder.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param parentId
	 *            - the parent Folder in which to create the new Folder
	 * @param name
	 *            - the Folder name
	 * @param description
	 *            - the Folder description
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public static void createFolder(OAuth2Token token, String parentId, String name, String description)
			throws IOException, ParseException {
		URL url = new URL(
				String.format("https://%s/sf/v3/Items(%s)/Folder", baseFunctions.getHostname(token), parentId));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("POST");
		baseFunctions.addAuthorizationHeader(connection, token);
		connection.addRequestProperty("Content-Type", "application/json");
		connection.setDoOutput(true);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		Map<String, Object> folder = new HashMap<String, Object>();
		folder.put("Name", name);
		folder.put("Description", description);
		Gson gson = new Gson();
		String body = gson.toJson(folder);

		connection.getOutputStream().write(body.getBytes());

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			JsonObject item = (JsonObject) parser.parse(response.toString());

			System.out.println("Created folder: " + item.get("Id").getAsString());
		}
	}

	/**
	 * Update the name and description of an Item.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param id
	 *            - the id of the Item to update
	 * @param name
	 *            - the new name of the Item
	 * @param description
	 *            - the new description of the Item
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public static void updateItem(OAuth2Token token, String id, String name, String description)
			throws IOException, ParseException {
		URL url = new URL(String.format("https://%s/sf/v3/Items(%s)", baseFunctions.getHostname(token), id));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.addRequestProperty("Content-Type", "application/json");
		baseFunctions.addAuthorizationHeader(connection, token);
		connection.setDoOutput(true);
		// HttpUrlConnection does not support the PATCH method by default
		connection.addRequestProperty("X-HTTP-Method-Override", "PATCH");
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		Map<String, Object> folder = new HashMap<String, Object>();
		folder.put("Name", name);
		folder.put("Description", description);
		Gson gson = new Gson();
		String body = gson.toJson(folder);

		connection.getOutputStream().write(body.getBytes());

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);
		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			System.out.println("Updated folder");
		}
		connection.disconnect();
	}

	/**
	 * Delete an Item by Id.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param id
	 *            - the Id of the Item to delete
	 * @throws java.text.ParseException
	 * @throws IOException
	 */
	public static void deleteItem(OAuth2Token token, String id) throws IOException, ParseException {
		URL url = new URL(String.format("https://%s/sf/v3/Items(%s)", baseFunctions.getHostname(token), id));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("DELETE");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());

		connection.disconnect();
	}

	/**
	 * Download a single Item.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param id
	 *            - the Id of the Item to download
	 * @param localFilePath
	 *            - full path including file name of download destination.
	 *            should end in .zip for a Folder
	 * @throws java.text.ParseException
	 * @throws IOException
	 */
	public static void downloadItem(OAuth2Token token, String id, String localFilePath)
			throws IOException, ParseException {
		URL url = new URL(String.format("https://%s/sf/v3/Items(%s)/Download", baseFunctions.getHostname(token), id));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		InputStream in = connection.getInputStream();
		FileOutputStream fos = new FileOutputStream(localFilePath);
		byte[] buffer = new byte[1024 * 1024];
		int length;
		while ((length = in.read(buffer)) > 0) {
			fos.write(buffer, 0, length);
		}

		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());

		in.close();
		fos.close();
		connection.disconnect();
	}

	/**
	 * Uploads a File using the Standard upload method with a multipart/form
	 * mime encoded POST.
	 *
	 * @param token
	 *            - the OAuth2Token returned from authenticate
	 * @param parentId
	 *            - where to upload the File
	 * @param localPath
	 *            - the path of the file to upload, like
	 *            "c:\\path\\to\\file.name"
	 * @throws java.text.ParseException
	 * @throws IOException
	 */
	public static void uploadFile(OAuth2Token token, String parentId, String localPath)
			throws IOException, ParseException {
		URL url = new URL(
				String.format("https://%s/sf/v3/Items(%s)/Upload", baseFunctions.getHostname(token), parentId));
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();
		connection.setRequestMethod("GET");
		baseFunctions.addAuthorizationHeader(connection, token);
		System.out.println(connection.getRequestMethod() + " " + connection.getURL());
		connection.connect();

		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}

		System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
		System.out.println(response);

		if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
			JsonParser parser = new JsonParser();
			JsonObject uploadConfig = (JsonObject) parser.parse(response.toString());

			JsonElement element = uploadConfig.get("ChunkUri");
			if (element != null) {
				String chunkUri = element.getAsString();
				baseFunctions.multipartUploadFile(localPath, chunkUri);
			} else {
				System.out.println("Did not receive an Upload URL");
			}
		}
	}

	private static void multipartUploadFile(String localPath, String uploadUrl)
			throws MalformedURLException, IOException {
		URL url = new URL(uploadUrl);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		String boundary = "--" + UUID.randomUUID().toString();

		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

		File file = new File(localPath);
		String filename = file.getName();

		InputStream source = new FileInputStream(file);
		OutputStream target = connection.getOutputStream();

		StringBuffer buffer = new StringBuffer();
		buffer.append("--" + boundary + "\r\n");
		buffer.append("Content-Disposition: form-data; name=File1; filename=\"" + filename + "\"\r\n");
		String contentType = HttpURLConnection.guessContentTypeFromName(filename);
		if (contentType == null) {
			contentType = "application/octet-stream";
		}
		buffer.append("Content-Type: " + contentType + "\r\n\r\n");

		target.write(buffer.toString().getBytes());

		// read from file, and write to outputstream
		byte[] buf = new byte[1024 * 1024];
		int len;
		while ((len = source.read(buf, 0, buf.length)) >= 0) {
			target.write(buf, 0, len);
		}
		target.flush();

		target.write(("\r\n--" + boundary + "--\r\n").getBytes());

		// get Response
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuffer response = new StringBuffer();
		String line = null;
		while ((line = reader.readLine()) != null) {
			response.append(line).append("\n");
		}
		reader.close();
		System.out.println(response.toString());

		target.close();
		source.close();
		connection.disconnect();
	}
}
