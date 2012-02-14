package de.tudarmstadt.botnet.janus_yanai;

import org.json.JSONObject;

abstract class Action {
	abstract String getToken();
	abstract JSONObject call(String args) throws Exception;
}