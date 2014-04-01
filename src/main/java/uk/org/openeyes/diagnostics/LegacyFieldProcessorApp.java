/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.org.openeyes.diagnostics;

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
public class LegacyFieldProcessorApp {

  public static void main(String[] args) {

    if (args.length == 0) {
      System.out.println("Error: no options specified. Try -h");
      System.exit(1);
    }
    Options options = new Options();
    Option optionFile = new Option("f", "file", true,
            "Specify XML file to send, then quit (mutually exclusive with -i)");
    Option optionInDir = new Option("d", "dir", true,
            "Directory to watch for new files.");
    Option optionRegex = new Option("r", "regex", true,
            "Regular expression to determine valid patient identifiers."
			+ " Defaults to " + AbstractFieldProcessor.DEFAULT_REGEX);
    Option optionGlobalSearchPath = new Option("g", "global-search-path", true,
            "Specify search path for applications (like ImageMagick, for example).");
    Option optionErrDir = new Option("e", "errDir", true,
            "Dirctory to move files that that failed validation.");
    Option optionArchiveDir = new Option("a", "archive-dir", true,
            "Directory to move sucessfully transferred files to.");
    Option optionLegacy = new Option("l", "legacy-dir", true,
            "Do not transfer files via the API; Rather, package them as OE-"
			+ "compatible resources and write them to file in the specified"
			+ " directory, the purpose being to run a separate script to" 
			+ " transfer them.");
    Option optionImageOpts = new Option("o", "image-options", true,
            "Specify location and segment of humphrey test to extract, along with"
			+ " scaling parameters. Format: x,y,w,h,x1,y1 where x,y is the"
			+ " the location to cut image with wxh size, scaled to x1,y1.");
    Option optionSource = new Option("x", "xml-source", false,
            "Include XML source file information with captured data. Default"
			+ " is false.");
    Option optionHelp = new Option("h", "help", false,
            "Print this help then quit.");
    options.addOption(optionErrDir);
    options.addOption(optionFile);
    options.addOption(optionInDir);
    options.addOption(optionArchiveDir);
    options.addOption(optionImageOpts);
    options.addOption(optionGlobalSearchPath);
    options.addOption(optionLegacy);
    options.addOption(optionSource);
    options.addOption(optionHelp);
    options.addOption(optionRegex);
    CommandLineParser parser = new PosixParser();
    LegacyFieldProcessor watcher = new LegacyFieldProcessor();
    try {
      CommandLine cmd = parser.parse(options, args);
      if (cmd.hasOption("help") || cmd.hasOption('h')) {
        FhirUtils.printHelp(options);
      }
      if (cmd.hasOption("d") || cmd.hasOption("dir")) {
        watcher.setDir(new File(cmd.getOptionValue("dir")));
        if (!watcher.getDir().exists()) {
          System.err.println(watcher.getDir().getAbsolutePath()
                  + " does not exist. Specify a valid watch directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("r") || cmd.hasOption("regex")) {
        watcher.setRegex(cmd.getOptionValue("regex"));
      }
      if (cmd.hasOption("g") || cmd.hasOption("global-search-path")) {
        watcher.setGlobalSearchPath(cmd.getOptionValue("global-search-path"));
      }
      if (cmd.hasOption("e") || cmd.hasOption("errDir")) {
        watcher.setErrDir(new File(cmd.getOptionValue("errDir")));
        if (!watcher.getErrDir().exists()) {
          System.err.println(watcher.getErrDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("l") || cmd.hasOption("legacy")) {
        watcher.setLegacyDir(new File(cmd.getOptionValue("legacy-dir")));
        if (!watcher.getLegacyDir().exists()) {
          System.err.println(watcher.getArchiveDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("a") || cmd.hasOption("archive")) {
        watcher.setArchiveDir(new File(cmd.getOptionValue("archive-dir")));
        if (!watcher.getArchiveDir().exists()) {
          System.err.println(watcher.getArchiveDir() + " does not exist. "
                  + "Specify a valid directory.");
          System.exit(1);
        }
      }
      if (cmd.hasOption("o") || cmd.hasOption("image-options")) {
        watcher.setImageOptions(cmd.getOptionValue("image-options"));
      }
      if (cmd.hasOption("x") || cmd.hasOption("xml-source")) {
        watcher.setIncludeSource(true);
      }
      if (cmd.hasOption("f") || cmd.hasOption("file")) {
        String file = cmd.getOptionValue("file");
		watcher.processFile(new File(file));
      }
    } catch (Exception ex) {
		ex.printStackTrace();
      System.err.println("Error: " + ex.getMessage());
    }
  }
}
