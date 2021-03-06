package cz.ivantichy.httpapi.handlers.vpnapi;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;

import cz.ivantichy.base64.B64;
import cz.ivantichy.fileutils.FileWork;
import cz.ivantichy.httpapi.executors.CommandExecutor;
import cz.ivantichy.koncentrator.simple.IPUtils.IPMaskConverter;
import cz.ivantichy.supersimple.restapi.handlers.interfaces.PUTHandlerInterface;
import cz.ivantichy.supersimple.restapi.server.PUTRequest;
import cz.ivantichy.supersimple.restapi.server.Response;
import cz.ivantichy.supersimple.restapi.staticvariables.Static;

public class CreateServerAdapter extends CommandExecutor implements
		PUTHandlerInterface {
	private static final Logger log = LogManager
			.getLogger(CreateServerAdapter.class.getName());

	@Override
	public Response handlePUT(PUTRequest req) throws IOException {
		clear();

		log.debug("PUT data: " + req.putdata);

		log.info("going to handle PUT. Reading/parsing JSON.");
		JSONObject json = new JSONObject(req.putdata);

		String destination = Static.OPENVPNLOCATION + Static.INSTANCESFOLDER
				+ json.getString("subvpn_type") + Static.FOLDERSEPARATOR
				+ json.getString("subvpn_name") + Static.FOLDERSEPARATOR;
		log.info("Destination location:" + destination);

		String sourceconfigpath = destination + slash + "server.conf";
		log.info("Going to read config: " + sourceconfigpath);
		String config = FileWork.readFile(sourceconfigpath);
		log.debug("Config read: " + config);

		String cajsonfile = destination + slash + json.getString("subvpn_name")
				+ ".json";
		log.info("Reading CA JSON: " + cajsonfile);
		JSONObject cajson = new JSONObject(FileWork.readFile(cajsonfile));
		log.debug("CA JSON: " + cajson.toString());

		json.merge(cajson);

		if (json.keySet().contains("ip_server")) {

			json.put("ip_range", IPMaskConverter.maskToRange(
					json.getString("ip_server"), json.getString("ip_mask")));
		} else if (json.keySet().contains("ip_range")) {

			json.put("ip_server", IPMaskConverter.rangeToIPAddress(json
					.getString("ip_range")));
			json.put("ip_mask",
					IPMaskConverter.rangeToMask(json.getString("ip_range")));

		}

		if (!json.keySet().contains("ip_range")) {
			throw new IOException("Missing server ip_range");

		}

		log.info("Going to fill config templace");

		config = fillConfig(config, json);

		String destconfigpath = Static.OPENVPNLOCATION
				+ json.getString("subvpn_type") + "_" + json.get("subvpn_name");

		log.info("Destination config file path: " + destconfigpath);
		FileWork.saveFile(destconfigpath, config);

		log.debug("Config file written: \n" + config);

		appendLine("set -ex \n");
		appendLine("cd " + destination + Static.FOLDERSEPARATOR + "cmds\n");
		appendLine("./createsubvpn.sh {subvpn_name} {subvpn_type} {ip_range}\n");

		exec(json);
		json.put("destination", destination.replaceAll("//", "/"));

		json.put("server_conf_base64", B64.encode(config));

		storeJSON(json, destination + slash + json.getString("subvpn_name")
				+ ".json");

		log.info("JSON stored");
		log.debug("Stored CA/Server JSON: " + json.toString());

		return new Response(json.toString(), true);
	}

	private String fillConfig(String config, JSONObject json) {

		config = replaceField("server_port", config, json);
		config = replaceField("server_protocol", config, json);
		config = replaceField("server_management_port", config, json);
		config = replaceField("server_device", config, json);
		config = replaceField("subvpn_name", config, json);
		config = replaceField("subvpn_type", config, json);
		config = replaceField("ta", config, json);
		config = replaceField("ca", config, json);
		config = replaceField("key", config, json);
		config = replaceField("cert", config, json);
		config = replaceField("dh", config, json);
		config = replaceField("dh_size", config, json);
		config = replaceField("ip_server", config, json);
		config = replaceField("ip_mask", config, json);
		config += System.lineSeparator()
				+ json.getString("server_commands").replaceAll("[,]",
						System.lineSeparator());

		return config;

	}

}
