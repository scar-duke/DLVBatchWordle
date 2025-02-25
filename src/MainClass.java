/**
 * DLVBatchWordle
 * A Java program that solves wordles in batch using an ASP solver (DLV2) embedded using
 * the <a href="https://www.mat.unical.it/calimeri/projects/embasp/">EmbASP Project</a>.
 * 
 * @author Kaylynn Borror and Alan Ferrenberg
 */
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import it.unical.mat.embasp.base.Handler;
import it.unical.mat.embasp.base.InputProgram;
import it.unical.mat.embasp.base.OptionDescriptor;
import it.unical.mat.embasp.languages.asp.ASPInputProgram;
import it.unical.mat.embasp.languages.asp.AnswerSet;
import it.unical.mat.embasp.languages.asp.AnswerSets;
import it.unical.mat.embasp.platforms.desktop.DesktopHandler;
import it.unical.mat.embasp.specializations.dlv.DLVFilterOption;
import it.unical.mat.embasp.specializations.dlv2.desktop.DLV2DesktopService;

// To run from the command line if in root directory:
//      java -cp "lib/antlr-4.7.2-complete.jar;lib/embASP.jar;." src/MainClass.java
public class MainClass {

    private static Handler handler;
    private static String aspFile = "executable/dlv-2.1.2-linux-x86_64";
    //private static String aspFile = "executable/dlv-2.1.2-win64.exe";
    private static String wordFile = "words.txt";
    private static String startingWord = "slate";
    private static String mode = "add";
    private static String exportFile = mode + "-results.csv";

    private static final int MAX_TRIES = 8;
    
    public static void main(String[] args) {
        
        try {
            wordFile = args[0];
            mode = args[1];
            startingWord = args[2];
            exportFile = mode + "-results.csv";
        } catch (Exception e) {
            System.out.println("usage: java -cp \"lib/antlr-4.7.2-complete.jar;lib/embASP.jar;.\" src/MainClass.java readInFileName mode startingWord");
            System.exit(1);
        }

        try {
            OptionDescriptor options = new DLVFilterOption("");
            options.clear(); // clearing is required bc the default options are set up wrong
            options.addOption("--filter=winner/2,sgCount/1");
            
            handler = new DesktopHandler(new DLV2DesktopService(aspFile));
            handler.addOption(options);
            
            // Read in words to guess
            ArrayList<String> words = new ArrayList<String>();
            try {
                Scanner file = new Scanner(new File(wordFile));
                while (file.hasNext()) {
                    words.add(file.next());
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(2);
            }
            
            // Read in DLV files based on mode asked for
            addDLVProgram("programs/solutions-edb.dlv");
            if (mode.equals("add")) {
                addDLVProgram("programs/solutions-value-add.dlv");
                addDLVProgram("programs/alanbot.dlv");
            } else if (mode.equals("mult")) {
                addDLVProgram("programs/solutions-value-mult.dlv");
                addDLVProgram("programs/alanbot.dlv");
            } else if (mode.equals("info")) {
                addDLVProgram("programs/solutions-value-mult.dlv");
                addDLVProgram("programs/solutions-pattern.dlv");
                addDLVProgram("programs/solutions-plogp.dlv");
                addDLVProgram("programs/infosolver.dlv");
            } else {
                System.out.println("Incorrect mode: requires \"mult\", \"add\", or \"info\".");
                System.exit(3);
            }
            
            PrintWriter out = new PrintWriter(new File(exportFile));
            
            for (String word : words) {
                // Set up an arraylist to hold our clues programs so we can remove them later
                ArrayList<Integer> tempPrograms = new ArrayList<Integer>();
                String answer = word;
                out.print(word + ",");
                String wordProgression = startingWord + ",";
                
                // If we got super lucky and our starting word is the answer, print as such and move to the next word
                if (startingWord.equals(answer)) {
                    out.write(1 + "," + wordProgression + "\n");
                    System.out.println(startingWord + " - Tries: " + 1);
                } else {
                    // Generate our first set of clues
                    tempPrograms.add(generateClues(startingWord, answer, 0));
            
                    // Generate the next guess based on the clues created until we get the answer
                    for (int i = 1; i < MAX_TRIES; i++) {
                        String[] guess = generateWord();
                    
                        // If we messed up our DLV program somewhere and it evaluates to INCOHERENT, exit gracefully
                        if (guess.length == 0) {
                            System.out.println("Incoherent answerset encountered -- check your DLV program(s).");
                            System.exit(4);
                        }
                        
                        wordProgression += guess[1] + "," + guess[0] + ",";
                        // If we've guessed the answer, break out of the guess cycle
                        if (guess[0].equals(answer)) {
                            out.write((i+1) + "," + wordProgression + "\n");
                            System.out.println(guess[0] + " - Tries: " + (i+1));
                            break;
                        }
                    
                        // If we haven't guessed our answer yet, generate the next set of clues
                        tempPrograms.add(generateClues(guess[0], answer, i));
                    }
                
                    // Clean up our temporary clues to start clean for the next word
                    for (int id : tempPrograms) {
                        handler.removeProgram(id);
                    }
                }
            }
            
            // close our file after we've processed all words
            out.close();
            
        } catch (Exception e) {
            e.printStackTrace();
            //System.out.println("DLV file is set up incorrectly, make sure that the "
            //        + "executable is correct and that the DLV programs are valid.");
        }

    }

    /**
     * This method takes a DLV file and reads through it to add the program's
     * contents to the active Handler for the Answer Set Solver in this program
     * while removing any comments or show statements. This method assumes that 
     * comments and show statements are on their own lines.
     * 
     * @param programFileName a String containing the path and name of the DLV program's file
     * 
     * @return an int value representing the id of the DLV program on the Handler
     */
    private static int addDLVProgram(String programFileName) {
        InputProgram input = new ASPInputProgram();
        StringBuilder program = new StringBuilder();

        try {
            Scanner file = new Scanner(new File(programFileName));

            while (file.hasNextLine()) {
                String line = file.nextLine();
                if (!line.contains("%") && !line.contains("#show")) {
                    program.append(line);
                }
            }

        } catch (Exception e) {
            System.out.println("Invalid setup, make sure your file names are correct.");
            return -1;
        }

        // take the file outputs and add it to the input program
        input.addProgram(program.toString());

        // add the input program to the handler
        return handler.addProgram(input);
    }
    
    /**
     * This method runs the DLV solver using the loaded-in files and collects
     * the best word guess from the valid answerset(s). This method only grabs
     * the first best word if there is more than one valid answerset, and it
     * expects the format of the answerset atoms to be: score(String, int). If
     * the solver evaluates to INCOHERENT, it will return back the String "INCOHERENT"
     * 
     * @return a String value of the best word guess based on the DLV logic loaded in, or INCOHERENT
     */
    private static String[] generateWord() {
        // Start the process of generating answer sets based on the rules
        AnswerSets answerSets = (AnswerSets) handler.startSync();
        //ArrayList<String> word = new ArrayList<String>();
        //int score = -1;
        
        List<AnswerSet> sets = answerSets.getOptimalAnswerSets();
        
        // Yoink the optimal word from the answer set results
        for (AnswerSet answerSet : sets) {
            String set = answerSet.toString();
            
            return new String[]{set.substring(set.indexOf("winner(")+7, set.indexOf(",", set.indexOf("winner(")+7)), 
                        set.substring(set.indexOf("sgCount(")+8, set.indexOf(")", set.indexOf("sgCount(")+8))};
            //word.add(set.substring(set.indexOf("(")+1, set.indexOf(",")));
            //score = Integer.parseInt(set.substring(set.indexOf(",")+1, set.indexOf(")")));
        }
        //if (word.size() > 1) {
            // if there's more than one optimal word, choose the first one (this exists in case it
            //    should be changed later)
        //    return word.get(0);
        //}
        return new String[0];
    }
    
    /**
     * This method takes a guessed word and compares which letters are in the correct
     * word, which letters are not in the correct word, and which letters are in
     * the word, but in the wrong location, creates a clue index string representing
     * these options, then loads the result into a DLV program that is added to the
     * handler for future processing.
     * 
     * @param guess the word being guessed
     * @param correct the correct word to compare the guessed word against
     * @param tries 
     * 
     * @return an int value representing the id of the DLV program containing word clues on the Handler
     */
    private static int generateClues(String guess, String correct, int tries) {
        // First pass
        String clueIndex = passOne(guess, correct);
        
        // Second pass
        clueIndex = passTwo(guess, correct, clueIndex);
        
        // Write Clues in a DLV program
        int clueFileId = generateClueFile(guess, clueIndex, tries);
                
        return clueFileId;
    }
    
    /**
     * This method does a first pass through the guessed word to see what letters are in the
     * correct place ('g'), and which letters are not in the correct place ('x') and creates
     * a string representation of this to serve as clues to the correct word.
     * 
     * @param guess the word being guessed
     * @param correct the correct word to compare the guessed word against
     * 
     * @return a String in the format 'xxxxx' containing 'x' and/or 'g' after the first pass
     */
    private static String passOne(String guess, String correct) {
        String clueIndex = "";
        
        for (int i = 0; i < 5; i++) {
            if (guess.charAt(i) == correct.charAt(i)) {
                clueIndex += "g";
            } else {
                clueIndex += "x";
            }
        }
        return clueIndex;
    }
    
    /**
     * This method does a second pass through the guessed word to see if letters that were
     * not in the correct place during pass one are somewhere else in the word. If they are
     * contained in the word, but not at the right place, then the letter location in the 
     * clue index is set to 'y'.
     * 
     * @param guess the word being guessed
     * @param correct the correct word to compare the guessed word against
     * @param clueIndex the clue index from pass one
     * 
     * @return a String in the format 'xxxxx' containing 'x' and/or 'g' and/or 'y' after the second pass
     */
    private static String passTwo(String guess, String correct, String clueIndex) {
        HashMap<Character, Integer> fCount = new HashMap<Character, Integer>();
        StringBuilder newClues = new StringBuilder(clueIndex);
        
        for (int i = 0; i < 5; i++) {
            if (clueIndex.charAt(i) == 'x') {
                char checkLet = guess.charAt(i);
                
                int correctCount = countLetter(checkLet, correct);
                int greenCount = countGreen(checkLet, correct, clueIndex);
                fCount.put(checkLet, fCount.containsKey(checkLet) ? fCount.get(checkLet) + greenCount : greenCount);
                for (int j = 0; j < 5; j++) {

                    if (clueIndex.charAt(j) != 'g') {
                        char correctLet = correct.charAt(j);
                        if ((checkLet == correctLet) && (fCount.get(correctLet) < correctCount) && newClues.charAt(i) != 'y') {
                            newClues.setCharAt(i, 'y');
                            fCount.put(correctLet, fCount.containsKey(correctLet) ? fCount.get(correctLet) + 1 : 1);
                        }
                    }
                }
            }
        }
        return newClues.toString();
    }
    
    /**
     * This method takes the clue index generated from comparing the guessed word against the correct
     * word and creates DLV atoms, which are then passed into the Handler for this program so that
     * the next guessed word can use these clues to get closer to the final properly guessed word.
     * 
     * @param guess the word being guessed
     * @param clueIndex the clue index containing letter inclusion and locations
     * @param tries 
     * 
     * @return an int value representing the id of the DLV program containing word clues on the Handler
     */
    private static int generateClueFile(String guess, String clueIndex, int tries) {
        String program = "myTurn(" + (tries+1) + ").\n";
        
        for (int i = 0; i < 5; i++) {
            char letter = guess.charAt(i);
            
            if (clueIndex.charAt(i) == 'g') {
                program += "green(" + letter + ", " + (i+1) + ").\n";
            } else if (clueIndex.charAt(i) == 'y') {
                program += "yellow(" + letter + ", " + (i+1) + ", " + (tries+1) + ").\n";
            } else {
                // The letter is gray in that spot, see if it needs to be completely out
                boolean keepGray = true;
                for (int j = 0; j < 5; j++) {
                    if (j == i) {
                        continue;
                    }
                    char compareLet = guess.charAt(j);
                    if (compareLet == letter && clueIndex.charAt(j) != 'x') {
                        keepGray = false;
                        break;
                    }
                }
                if (keepGray) {
                    program += "gray(" + letter + ").\n";
                } else {
                    program += "out(" + letter + ", " + (i+1) + ").\n";
                }
            }
        }
        
        // Add program to handler
        //System.out.println(program);
        
        InputProgram input = new ASPInputProgram();
        input.addProgram(program);
        
        return handler.addProgram(input);
    }
    
    /**
     * This method counts the number of occurrences of a letter in a specific word passed in
     * 
     * @param letter the letter to be counted
     * @param word the word to be searched
     * @return the number of times letter shows up in word
     */
    private static int countLetter(char letter, String word) {
        int count = 0;
        
        for (int i = 0; i < word.length(); i++) {
            if (letter == word.charAt(i)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * This method counts the number of times a letter is green (in the correct location) in a specified word
     * 
     * @param let the letter to be counted
     * @param word the word to be searched
     * @param mask a String in the form of 'xxxxx' where the letter is green if the character is 'g'
     * 
     * @return the number of times let is green in word
     */
    private static int countGreen(char let, String word, String mask) {
        int count = 0;
        
        for (int i = 0; i < word.length(); i++) {
            if (word.charAt(i) == let && mask.charAt(i) == 'g') {
                count++;
            }
        }
        return count;
    }

}
