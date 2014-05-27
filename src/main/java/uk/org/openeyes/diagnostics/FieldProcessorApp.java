/**
 * OpenEyes
 *
 * (C) Moorfields Eye Hospital NHS Foundation Trust, 2008-2011
 * (C) OpenEyes Foundation, 2011-2013
 * This file is part of OpenEyes.
 * OpenEyes is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * OpenEyes is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with OpenEyes in a file titled COPYING. If not, see <http://www.gnu.org/licenses/>.
 *
 * @package OpenEyes
 * @link http://www.openeyes.org.uk
 * @author OpenEyes <info@openeyes.org.uk>
 * @copyright Copyright (c) 2008-2011, Moorfields Eye Hospital NHS Foundation Trust
 * @copyright Copyright (c) 2011-2013, OpenEyes Foundation
 * @license http://www.gnu.org/licenses/gpl-3.0.html The GNU General Public License V3.0
 */
package uk.org.openeyes.diagnostics;

import java.io.Console;
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
