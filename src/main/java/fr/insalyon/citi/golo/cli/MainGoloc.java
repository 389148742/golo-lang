/*
 * Copyright 2012-2013 Institut National des Sciences Appliquées de Lyon (INSA-Lyon)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.insalyon.citi.golo.cli;

import fr.insalyon.citi.golo.compiler.GoloCompilationException;
import fr.insalyon.citi.golo.compiler.GoloCompiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import static fr.insalyon.citi.golo.cli.MainGoloGolo.handleCompilationException;

public class MainGoloc {

  private static enum Options {
    output("the compiled classes output directory"),
    version("prints the software version"),
    fullversion("prints the software version and build number"),
    help("prints this message "),;

    final String description;

    private Options(String description) {
      this.description = description;
    }
  }

  public static void main(String... args) {
    if (args.length == 0) {
      help();
      return;
    }

    List<String> sources = new LinkedList<>();
    String output = ".";
    int i = 0;
    try {
      while (i < args.length) {
        String arg = args[i];
        if (arg.startsWith("-")) {
          arg = arg.substring(1);
          switch (Options.valueOf(arg)) {
            case output:
              i = i + 1;
              if (i >= args.length) {
                help();
                return;
              }
              output = args[i];
              break;
            case version:
              version();
              return;
            case fullversion:
              fullversion();
              return;
            case help:
              help();
              return;
          }
        } else {
          sources.add(arg);
        }
        i = i + 1;
      }
    } catch (IllegalArgumentException e) {
      help();
    }

    GoloCompiler compiler = new GoloCompiler();
    File outputDir = new File(output);
    for (String source : sources) {
      File file = new File(source);
      try (FileInputStream in = new FileInputStream(file)) {
        compiler.compileTo(file.getName(), in, outputDir);
      } catch (IOException e) {
        System.out.println("[error] " + source + " does not exist or could not be opened.");
        return;
      } catch (GoloCompilationException e) {
        handleCompilationException(e);
      }
    }
  }

  private static void version() {
    System.out.println(Metadata.VERSION);
  }

  private static void fullversion() {
    System.out.println(versionAndBuild());
  }

  private static void help() {
    System.out.println("Golo compiler " + versionAndBuild());
    System.out.println();
    System.out.println("Usage: goloc <options> file1.golo file2.golo (...)");
    System.out.println("where options include:");
    for (Options option : Options.values()) {
      System.out.println("    -" + option.name());
      System.out.println("         " + option.description);
    }
  }

  private static String versionAndBuild() {
    return Metadata.VERSION + " (build " + Metadata.TIMESTAMP + ")";
  }
}
