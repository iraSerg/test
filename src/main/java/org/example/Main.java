package org.example;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.*;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    private static final Pattern RVRE = Pattern.compile("^(.*?[" +
            "аеиоуыэюя])(.*)", Pattern.CASE_INSENSITIVE);

    private static final Pattern[] STEMMING_PATTERNS = {
            Pattern.compile("([ая])(в|вши|вшись)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(ив|ивши|ившись|ыв|ывши|ывшись)$"),
            Pattern.compile("(с[яь])$"),
            Pattern.compile("(ее|ие|ые|ое|ими|ыми|ей|ий|ый|ой|ем|им|ым|ом|его|ого|ему|ому|их|ых|ую|юю|ая|яя|ою|ею)$"),
            Pattern.compile("([ая])(ем|нн|вш|ющ|щ)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(ивш|ывш|ующ)$"),
            Pattern.compile("([ая])(ла|на|ете|йте|ли|й|л|ем|н|ло|но|ет|ют|ны|ть|ешь|нно)$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(ила|ыла|ена|ейте|уйте|ите|или|ыли|ей|уй|ил|ыл|им|ым|ен|ило|ыло|ено|ят|ует|уют|ит|ыт|ены|ить|ыть|ишь|ую|ю)$"),
            Pattern.compile("(а|ев|ов|ие|ье|е|иями|ями|ами|еи|ии|и|ией|ей|ой|ий|й|иям|ям|ием|ем|ам|ом|о|у|ах|иях|ях|ы|ь|ию|ью|ю|ия|ья|я)$"),
            Pattern.compile(".*[^аеиоуыэюя]+[аеиоуыэюя].*ость?$"),
            Pattern.compile("ость?$"),
            Pattern.compile("(ейше|ейш)$"),
            Pattern.compile("и$"),
            Pattern.compile("ь$"),
            Pattern.compile("нн$")
    };
    private static final List<String> STOP_WORDS = Arrays.asList(
            "и", "а", "но", "что", "где", "когда", "как", "в", "на", "за", "о", "по", "для", "зачем", "то", "я"
    );

    public static String removeStopWords(String text) {
        String result = text;

        for (String stopWord : STOP_WORDS) {
            result = result.replaceAll("\\b" + Pattern.quote(stopWord) + "\\b", "");
        }

        return result.trim();
    }

    public static String stemWord(String word) {
        word = word.replaceAll("ё", "е");
        Matcher matcher = RVRE.matcher(word);
        if (matcher.find()) {
            String start = matcher.group(1);
            String remainder = matcher.group(2);
            String temp = applyPattern(remainder, STEMMING_PATTERNS[1]);
            if (temp.equals(remainder)) {
                temp = applyPattern(remainder, STEMMING_PATTERNS[0]);
            }
            if (temp.equals(remainder)) {
                remainder = applyPattern(remainder, STEMMING_PATTERNS[2]);
                temp = applyPattern(remainder, STEMMING_PATTERNS[3]);
                if (!temp.equals(remainder)) {
                    remainder = temp;
                    temp = applyPattern(remainder, STEMMING_PATTERNS[5]);
                    if (temp.equals(remainder)) {
                        remainder = applyPattern(remainder, STEMMING_PATTERNS[4]);
                    }
                } else {
                    temp = applyPattern(remainder, STEMMING_PATTERNS[7]);
                    if (temp.equals(remainder)) {
                        temp = applyPattern(remainder, STEMMING_PATTERNS[6]);
                    }
                    if (temp.equals(remainder)) {
                        remainder = applyPattern(remainder, STEMMING_PATTERNS[8]);
                    } else {
                        remainder = temp;
                    }
                }
            } else {
                remainder = temp;
            }
            remainder = applyPattern(remainder, STEMMING_PATTERNS[11]);
            if (remainder.matches(STEMMING_PATTERNS[9].pattern())) {
                remainder = applyPattern(remainder, STEMMING_PATTERNS[10]);
            }
            temp = applyPattern(remainder, STEMMING_PATTERNS[12]);
            if (temp.equals(remainder)) {
                remainder = applyPattern(remainder, STEMMING_PATTERNS[13]);
                remainder = applyPattern(remainder, STEMMING_PATTERNS[14]);
            } else {
                remainder = temp;
            }
            return start + remainder;
        }
        return word;
    }

    private static String applyPattern(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            return matcher.replaceFirst("");
        }
        return input;
    }

    public static void main(String[] args) {
        if (args.length < 5 || !args[0].equals("--data") || !args[2].equals("--input-file") || !args[4].equals("--output-file")) {

            return;
        }

        String outputFilePath = args[5];
        String pathToCsv = args[1];
        String inputPathToFile = args[3];
        try {
            Root result = processData(pathToCsv, inputPathToFile);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);

            Files.write(new File(outputFilePath).toPath(), mapper.writeValueAsBytes(result));

        } catch (IOException e) {
            return;
        }


    }

    private static Root processData(String pathToCsv, String inputFilePath) throws IOException {
        long startTime = Instant.now().toEpochMilli();
        List<String> names = new ArrayList<>();
        HashMap<String, List<Integer>> wordsIndex = getWordsIndex(pathToCsv, names);
        long initTime = Instant.now().toEpochMilli() - startTime;

        List<ResultEntry> results = solve(wordsIndex, inputFilePath, names);

        Root root = new Root();
        root.setInitTime(initTime);
        root.setResult(results);

        return root;
    }

    private static HashMap<String, List<Integer>> getWordsIndex(String pathToScv, List<String> names) throws IOException {
        List<String> inputList = new ArrayList<>();


        HashMap<String, List<Integer>> wordIndex = new HashMap<>();

        File inputF = new File(pathToScv);
        try (InputStream inputFS = new FileInputStream(inputF); BufferedReader br = new BufferedReader(new InputStreamReader(inputFS))) {

            inputList = br.lines().toList();

            for (int i = 0; i < inputList.size(); i++) {
                String line = inputList.get(i);
                if (!line.isEmpty()) {
                    List<String> parts = getCsvParts(inputList.get(i).toLowerCase());
                    List<String> words = getWords(parts);
                    names.add(parts.get(1));
                    getListWordIndex(wordIndex, words, i);
                }


            }


            return wordIndex;

        }


    }

    private static void getListWordIndex(HashMap<String, List<Integer>> wordIndex, List<String> words, int i) {
        String stemStr;

        for (String word : words) {
            stemStr = stemWord(word);
            List<Integer> indices = wordIndex.get(stemStr);
            if (indices == null) {

                indices = new ArrayList<>();
                wordIndex.put(stemStr, indices);
            }


            indices.add(i);

        }
    }

    private static List<ResultEntry> solve(HashMap<String, List<Integer>> wordIndex, String inputFilePath, List<String> names) throws IOException {

        List<ResultEntry> results = new ArrayList<>();


        try (BufferedReader reader = new BufferedReader(new FileReader(inputFilePath))) {
            String line;


            while ((line = reader.readLine()) != null) {
                long searchTime = Instant.now().toEpochMilli();
                List<String> words = new ArrayList<>();
                List<String> stemWords = new ArrayList<>();

                words = getWordsFromLine(line.toLowerCase());
                for (String word : words) {
                    stemWords.add(stemWord(word));
                }

                Map<Integer, Integer> rating = getRating(stemWords, wordIndex);
                List<Integer> coincidences = getCoincidences(rating, words.size());
                searchTime = Instant.now().toEpochMilli() - searchTime;

                if (!coincidences.isEmpty()) {
                    List<String> matchedNames = new ArrayList<>();
                    for (int index : coincidences) {
                        matchedNames.add(names.get(index).trim());
                    }

                    ResultEntry entry = new ResultEntry();
                    entry.setSearch(line);
                    entry.setResult(matchedNames);
                    entry.setTime(searchTime);
                    results.add(entry);
                }


            }

        }
        return results;
    }

    private static List<String> getWords(List<String> parts) {
        List<String> words = new ArrayList<>();
        words = Arrays.stream(removeStopWords(parts.get(2)).toLowerCase().split("\\s*[.,;!?]\\s*|\\s+")).toList();

        return words;
    }

    private static List<String> getWordsFromLine(String line) {
        List<String> words = new ArrayList<>();
        words = Arrays.stream(removeStopWords(line).toLowerCase().split("\\s*[.,;!?]\\s*|\\s+")).toList();
        return words;
    }

    private static List<String> getCsvParts(String string) {
        List<String> parts = new ArrayList<>();
        parts = Arrays.stream(string.split("\\|")).toList();
        return parts;
    }

    private static Map<Integer, Integer> getRating(List<String> words, HashMap<String, List<Integer>> wordIndex) {
        Map<Integer, Integer> rating = new HashMap<>();
        for (int i = 0; i < words.size(); i++) {
            List<Integer> indices = wordIndex.get(words.get(i));
            if (indices != null) {
                for (Integer j : indices) {
                    if (rating.get(j) != null) {
                        rating.put(j, rating.get(j) + 1);
                    } else {
                        rating.put(j, 1);
                    }
                }


            }
        }

        return rating;
    }

    private static List<Integer> getCoincidences(Map<Integer, Integer> rating, int len) {
        List<Integer> coincidences = new ArrayList<>();
        int percent = len / 2;
        for (Map.Entry<Integer, Integer> entry : rating.entrySet()) {
            if (entry.getValue() >= percent) {
                coincidences.add(entry.getKey());
            }
        }

        return coincidences;
    }

    static class ResultEntry {
        private String search;
        private List<String> result;
        private long time;

        public String getSearch() {
            return search;
        }

        public void setSearch(String search) {
            this.search = search;
        }

        public List<String> getResult() {
            return result;
        }

        public void setResult(List<String> result) {
            this.result = result;
        }

        public long getTime() {
            return time;
        }

        public void setTime(long time) {
            this.time = time;
        }
    }

    static class Root {
        private long initTime;
        private List<ResultEntry> result;

        public long getInitTime() {
            return initTime;
        }

        public void setInitTime(long initTime) {
            this.initTime = initTime;
        }

        public List<ResultEntry> getResult() {
            return result;
        }

        public void setResult(List<ResultEntry> result) {
            this.result = result;
        }
    }


}

