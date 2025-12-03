package net.kdt.pojavlaunch;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Utility class for Steam-related functionality
 */
public class SteamUtil {

    /**
     * Get the current number of players for a Steam app
     * @param appId The Steam app ID (e.g., "99900" for Spiral Knights)
     * @return The current player count, or 0 if failed
     */
    public static int getCurrentPlayers(String appId) {
        try {
            String apiUrl = "https://api.steampowered.com/ISteamUserStats/GetNumberOfCurrentPlayers/v1/?appid=" + appId;
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JSONObject jsonResponse = new JSONObject(response.toString());
                JSONObject responseObj = jsonResponse.getJSONObject("response");
                int playerCount = responseObj.getInt("player_count");
                
                connection.disconnect();
                return playerCount;
            }
            
            connection.disconnect();
            return 0;
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Get the approximate player count for Spiral Knights (official servers)
     * This includes Steam players multiplied by 1.6x to account for standalone users
     * @return Approximate player count, or 0 if failed
     */
    public static int getOfficialApproxPlayerCount() {
        int steamPlayers = getCurrentPlayers("99900");
        if (steamPlayers == 0) {
            return 0;
        } else {
            return Math.round(steamPlayers * 1.6f);
        }
    }
}
