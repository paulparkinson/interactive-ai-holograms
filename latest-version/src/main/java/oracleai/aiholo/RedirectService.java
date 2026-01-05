package oracleai.aiholo;

import org.json.JSONObject;
import org.springframework.stereotype.Service;
import oracleai.common.GetSetController;

/**
 * Service class for handling redirect functionality when answers need to be stored
 * instead of immediately played as audio.
 */
@Service
public class RedirectService {

    private static final boolean REDIRECT_ANSWER = Configuration.isRedirectAnswerEnabled();

    /**
     * Execute redirect - stores answer parameters as JSON in GetSetController instead of playing audio
     */
    public void executeRedirect(String textToSay, String languageCode, String voiceName,
                               String aiPipelineLabel, double aiDurationMillis, String ttsSelection,
                               boolean audio2FaceEnabled) {
        try {
            JSONObject json = new JSONObject();
            json.put("textToSay", textToSay != null ? textToSay : "");
            json.put("languageCode", languageCode != null ? languageCode : "");
            json.put("voiceName", voiceName != null ? voiceName : "");
            json.put("aiPipelineLabel", aiPipelineLabel != null ? aiPipelineLabel : "");
            json.put("aiDurationMillis", aiDurationMillis);
            json.put("ttsSelection", ttsSelection != null ? ttsSelection : "");
            json.put("audio2FaceEnabled", audio2FaceEnabled);
            json.put("timestamp", System.currentTimeMillis());

            String jsonString = json.toString();
            GetSetController.setValue(jsonString);
            System.out.println("Redirect answer stored in GetSetController: " + jsonString);
        } catch (Exception e) {
            System.err.println("Error in executeRedirect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Check if redirect is enabled
     */
    public boolean isRedirectEnabled() {
        return REDIRECT_ANSWER;
    }
}