
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
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

public class MainClass {

    private static Handler handler;
    private static String aspFile = "executable/dlv-2.1.2-win64.exe";
    private static String idbFileName = "programs/icecream-in-out.dlv";
    private static String edbFileName = "programs/icecream-edb.dlv";

    public static void main(String[] args) {

        try {
            OptionDescriptor options = new DLVFilterOption("");
            options.clear(); // clearing is required bc the default options are set up wrong
            options.addOption("--filter=score/2");
            
            handler = new DesktopHandler(new DLV2DesktopService(aspFile));
            handler.addOption(options);
            
            // Read in DLV files very crudely and quickly for testing purposes
            addDLVProgram("programs/alanbot.dlv");
            addDLVProgram("programs/frequencySolutions-edb.dlv");
            addDLVProgram("programs/solutions-edb.dlv");
            addDLVProgram("programs/used.dlv");
            
            String guess = generateWord();

            System.out.println(guess);
            
            //int clueFileId = generateClues(guess, answer);
            
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
        String program = "";

        try {
            Scanner file = new Scanner(new File(programFileName));

            while (file.hasNextLine()) {
                String line = file.nextLine();
                if (!line.contains("%") && !line.contains("#show")) {
                    program += line;
                }
            }

        } catch (Exception e) {
            System.out.println("Invalid setup, make sure your file names are correct.");
            return -1;
        }

        // take the file outputs and add it to the input program
        input.addProgram(program);

        // add the input program to the handler
        return handler.addProgram(input);
    }
    
    /**
     * This method runs the DLV solver using the loaded-in files and collects
     * the best word guess from the valid answerset(s). This method only grabs
     * the first best word if there is more than one valid answerset, and it
     * expects the format of the answerset atoms to be: score(String, int)
     * 
     * This method does not function properly when the answerset is INCOHERENT
     * 
     * @return a String value of the best word guess based on the DLV logic loaded in
     */
    private static String generateWord() {
        // Start the process of generating answer sets based on the rules
        AnswerSets answerSets = (AnswerSets) handler.startSync();
        ArrayList<String> word = new ArrayList<String>();
        //int score = -1;
        
        // Yoink the optimal word from the answer set results
        for(AnswerSet answerSet : answerSets.getOptimalAnswerSets()) {
            // Format: [score(w,s)]
            String set = answerSet.toString();
            word.add(set.substring(set.indexOf("(")+1, set.indexOf(",")));
            //score = Integer.parseInt(set.substring(set.indexOf(",")+1, set.indexOf(")")));
        }
        //if (word.size() > 1) {
            // if there's more than one optimal word, choose the first one (this exists in case it
            //    should be changed later)
        //    return word.get(0);
        //}
        return word.get(0);
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
     * 
     * @return an int value representing the id of the DLV program containing word clues on the Handler
     */
    private static int generateClues(String guess, String correct) {
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
                if (fCount.containsKey(checkLet)) {
                    fCount.put(checkLet, fCount.get(checkLet) + countGreen(checkLet, correct, clueIndex));
                } else {
                    fCount.put(checkLet, countGreen(checkLet, correct, clueIndex));
                }
                for (int j = 0; j < 5; j++) {
                    if (i == j) {
                        continue;
                    }
                    if (clueIndex.charAt(j) != 'g') {
                        char correctLet = correct.charAt(j);
                        if ((checkLet == correctLet) && (fCount.get(correctLet) < correctCount)) {
                            newClues.setCharAt(i, 'y');
                            if (fCount.containsKey(correctLet)) {
                                fCount.put(correctLet, fCount.get(correctLet) + 1);
                            } else {
                                fCount.put(correctLet, 1);
                            }
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
        
        
        return -1;
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
