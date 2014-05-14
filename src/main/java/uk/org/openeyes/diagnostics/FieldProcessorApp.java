/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

import java.io.Console;
import java.io.File;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;

/**
 *
 * @author rich
 */
public class FieldProcessorApp {

	public static void main(String[] args) {

		if (args.length == 0) {
			System.out.println("Try -h");
			System.exit(1);
		}
		Options options = CommonOptions.getCommonOptions();
		Option optionHost = new Option("s", "host", true,
				"Specify server to send messages to.");
		Option optionPort = new Option("p", "port", true,
				"Port to connect to on server.");
		Option optionCredentials = new Option("c", "credentials", true,
				"Supply username/password (comma separated) for authentication.");
		Option optionInDir = new Option("d", "dir", true,
				"Directory to watch for new files.");
		Option optionDupDir = new Option("u", "duplicates", true,
				"Duplicate files (successfully transferred) are moved to this directory.");
		Option optionOutgoing = new Option("t", "outgoing", true,
				"Directory to place measurement files that were not successfully sent.");
		options.addOption(optionInDir);
		options.addOption(optionHost);
		options.addOption(optionCredentials);
		options.addOption(optionPort);
		options.addOption(optionDupDir);
		options.addOption(optionOutgoing);
		FieldProcessor watcher = new FieldProcessor();
		CommonOptions.parseCommonOptions(watcher, options, args);
		CommandLineParser parser = new PosixParser();
		try {
			CommandLine cmd = parser.parse(options, args);
			if (cmd.hasOption("help") || cmd.hasOption('h')) {
				FhirUtils.printHelp(options);
			}
			if (cmd.hasOption("s") || cmd.hasOption("host")) {
				watcher.setHost(cmd.getOptionValue("host"));
			}
			if (cmd.hasOption("t") || cmd.hasOption("outgoing")) {
				watcher.setOutgoingDir(cmd.getOptionValue("outgoing"));
			}
			if (cmd.hasOption("c") || cmd.hasOption("credentials")) {
				if (!cmd.getOptionValue("credentials").contains(",")) {
					System.err.println("Supply credentials separated by a comma (',').");
					System.exit(1);
				}
				String[] credentials = cmd.getOptionValue("credentials").split(",");
                                if (credentials.length == 1) {
                                    Console cnsl = System.console();;
                                    if (cnsl != null) {
                                        watcher.setAuthenticationPassword(
                                                new String(cnsl.readPassword("Enter authentication password: ")));
                                    }  
                                    watcher.setAuthenticationUsername(credentials[0]);
                                } else {
                                    watcher.setAuthenticationUsername(credentials[0]);
                                    watcher.setAuthenticationPassword(credentials[1]);
                                }
			}
			if (cmd.hasOption("p") || cmd.hasOption("port")) {
				String port = cmd.getOptionValue("port");
				try {
					watcher.setPort(Integer.parseInt(port));
				} catch (NumberFormatException nfex) {
					System.err.println("Invalid value: " + port);
					System.err.println("Specify port number as a positive integer.");
					System.exit(1);
				}
			}
		} catch (Exception ex) {
			System.err.println("Error: " + ex.getMessage());
		}
	}
}
