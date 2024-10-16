import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

public class Lexer {
    static String inputFilePath = "test.txt";

    // Error message styling
    public static final String RESET = "\033[0m";
    public static final String RED = "\033[31m";
    public static final String UNDERLINE = "\033[4m";

    // finds and returns tokens in a stream
    public static List<Token> tokenize(String token, int currentID, int lines) {
        List<Token> tokens = new ArrayList<>(); // tokens to return
        String leftOver = ""; // allows for backtracking in longest match strategy
        boolean found = false;

        while (true) {
            found = false;
            // check if current stream is a token
            if (isDelimiter(token)) {
                tokens.add(new Token(TokenClass.DELIMITER, token, currentID++));
                found = true;
            } else if (isFunctionName(token)) {
                tokens.add(new Token(TokenClass.FUNCTION_NAME, token, currentID++));
                found = true;
            } else if (isVariableName(token)) {
                tokens.add(new Token(TokenClass.VARIABLE_NAME, token, currentID++));
                found = true;
            } else if (isKeyword(token)) {
                tokens.add(new Token(TokenClass.KEYWORD, token, currentID++));
                found = true;
            } else if (isNumber(token)) {
                tokens.add(new Token(TokenClass.NUMBER, token, currentID++));
                found = true;
            } else if (isOperator(token)) {
                tokens.add(new Token(TokenClass.OPERATOR, token, currentID++));
                found = true;
            } else if (isText(token)) {
                tokens.add(new Token(TokenClass.TEXT, token, currentID++));
                found = true;
            }

            // if token has been found, continue with what is left in the stream, break
            // otherwise.
            if (found) {
                if (leftOver.length() > 0) {
                    token = leftOver;
                    leftOver = "";
                    continue;
                } else {
                    break;
                }

                // if token was not found, remove a char from end of stream and check again
            } else {
                leftOver = token.charAt(token.length() - 1) + leftOver;
                token = token.substring(0, token.length() - 1);
            }

            // if no tokens found by reading from start, try reading from end
            if (token.length() == 0) {
                List<Token> endTokens = new ArrayList<>();
                token = leftOver;

                while (leftOver.length() > 0) {
                    // if token not found, remove char from start of stream and check again
                    leftOver = leftOver.substring(1);
                    if (isDelimiter(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.DELIMITER, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isFunctionName(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.FUNCTION_NAME, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isVariableName(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.VARIABLE_NAME, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isKeyword(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.KEYWORD, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isNumber(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.NUMBER, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isOperator(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.OPERATOR, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    } else if (isText(leftOver)) {
                        endTokens.add(0, new Token(TokenClass.TEXT, leftOver, currentID));
                        token = token.substring(0, token.length() - leftOver.length());
                        leftOver = token;
                    }
                }

                // If no more tokens are found, generate error message
                System.out.print(RED + UNDERLINE + "\nLexical Error on line " + lines + ": Cannot tokenize \"" + RESET);
                System.out.print(UNDERLINE + " " + token + " ");
                System.out.println(UNDERLINE + RED + "\"." + RESET);

                // add a new error token with the part that could not be tokenized
                tokens.add(new Token(TokenClass.ERROR, token, currentID++));

                // add the newly found tokens with some ID numbering logic
                List<Token> newTokens = new ArrayList<>();
                for (int i = 0; i < endTokens.size(); i++) {
                    TokenClass newClass = endTokens.get(i).getType();
                    String newValue = endTokens.get(i).getValue();
                    newTokens.add(new Token(newClass, newValue, currentID++));
                }
                tokens.addAll(newTokens);
                return tokens;
            }
        }
        return tokens;
    }

    public static boolean isDelimiter(String token) {
        if (token.equals(",") || token.equals(";") || token.equals("(") || token.equals(")") || token.equals("{")
                || token.equals("}")) {
            return true;
        }
        return false;
    }

    public static boolean isVariableName(String token) {
        String regex = "^V_[a-z]([a-z]|[0-9])*$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    public static boolean isFunctionName(String token) {
        String regex = "^F_[a-z]([a-z]|[0-9])*$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    public static boolean isText(String token) {
        String regex = "^\"[A-Z][a-z]{0,7}\"$";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    public static boolean isNumber(String token) {
        String regex = "^(0|0\\.([0-9])*[1-9]|-0\\.([0-9])*[1-9]|[1-9]([0-9])*|-[1-9]([0-9])*|"
                + "[1-9]([0-9])*(\\.[0-9]*[1-9])|-([1-9]([0-9])*)\\.[0-9]*[1-9])$";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    public static boolean isKeyword(String token) {
        String regex = "\\b(begin|halt|if|input|print|return|text|then|void|else|end|main|num|skip)\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    public static boolean isOperator(String token) {
        if (token.equals("=") || token.equals("<")) {
            return true;
        }
        String regex = "\\b(eq|mul|not|sqrt|sub|and|add|div|grt|or|<|=)\\b";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(token);
        return matcher.matches();
    }

    private static void writeXMLToFile(Document doc, String filePath) throws TransformerException, IOException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        DOMSource domSource = new DOMSource(doc);
        StreamResult streamResult = new StreamResult(new FileWriter(filePath));

        transformer.transform(domSource, streamResult);
    }

    public static void main(String[] args) throws ParserConfigurationException, TransformerException {
        try {
            String outputFilePath = "output.xml";
            StreamReader fileCharReader = new StreamReader(inputFilePath);
            int currentID = 0;
            String sub_stream = "";
            int lineNumber = 1;
            Boolean reset = false;
            ArrayList<Token> tokens = new ArrayList<Token>();

            while (true) {
                try {
                    // read into sub_stream until a whitespace is found
                    while (!reset) {
                        CharWithLine charWithLine = fileCharReader.getNextChar();
                        char ch = charWithLine.getCharacter();
                        lineNumber = charWithLine.getLineNumber();
                        if (ch == ' ' || ch == '\n' || ch == '\r') {
                            reset = true;
                        } else {
                            sub_stream += ch;
                        }
                    }

                    // get tokens out of substream and add to the overall token set
                    if (reset) {
                        reset = false;
                        if (sub_stream.length() > 0) {
                            tokens.addAll(tokenize(sub_stream, currentID, lineNumber));
                            currentID = tokens.size();
                        }
                        sub_stream = "";
                    }

                } catch (IOException e) {
                    System.out.println("\n" + e.getMessage());
                    break;
                }
            }

            fileCharReader.close();
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            // root element
            Element rootElement = doc.createElement("TOKENSTREAM");
            doc.appendChild(rootElement);

            // TOK element for each token
            for (Token t : tokens) {
                Element tokElement = doc.createElement("TOK");

                Element idElement = doc.createElement("ID");
                idElement.appendChild(doc.createTextNode(String.valueOf(t.getID())));
                tokElement.appendChild(idElement);

                Element classElement = doc.createElement("CLASS");
                classElement.appendChild(doc.createTextNode(t.getType().toString()));
                tokElement.appendChild(classElement);

                Element wordElement = doc.createElement("WORD");
                wordElement.appendChild(doc.createTextNode(t.getValue()));
                tokElement.appendChild(wordElement);
                rootElement.appendChild(tokElement);
            }

            writeXMLToFile(doc, outputFilePath);

            /* //Console printing for readability
            for (Token t : tokens) {
                if (t.getType() == TokenClass.ERROR) {
                    System.out.println(RED + t.getID() + ": Class = " + t.getType() + ", Value = " + t.getValue() + RESET);
                } else {
                    System.out.println(t.getID() + ": Class = " + t.getType() + ", Value = " + t.getValue());
                }
            }
            */

        } catch (IOException e) {
            System.out.println("An error occurred while reading the file: " + e.getMessage());
        }
    }
}

// object containing a char and its line number
class CharWithLine {
    private char character;
    private int lineNumber;

    public CharWithLine(char character, int lineNumber) {
        this.character = character;
        this.lineNumber = lineNumber;
    }

    public char getCharacter() {
        return character;
    }

    public int getLineNumber() {
        return lineNumber;
    }
}

// object that reads the input stream
class StreamReader {
    private BufferedReader reader;
    private int nextChar;
    private int lineNumber; // Add a lineNumber field

    public StreamReader(String fileName) throws IOException {
        reader = new BufferedReader(new FileReader(fileName));
        nextChar = reader.read();
        lineNumber = 1; // Start from the first line
    }

    // returns next char and its line number
    public CharWithLine getNextChar() throws IOException {
        if (nextChar == -1) {
            throw new IOException("End of input file reached.");
        }
        char currentChar = (char) nextChar;

        if (currentChar == '\n') {
            lineNumber++;
        }

        nextChar = reader.read();
        return new CharWithLine(currentChar, lineNumber);
    }

    public void close() throws IOException {
        if (reader != null) {
            reader.close();
        }
    }
}

enum TokenClass {
    DELIMITER,
    VARIABLE_NAME,
    FUNCTION_NAME,
    OPERATOR,
    TEXT,
    NUMBER,
    KEYWORD,
    ERROR;
}

class Token {
    private TokenClass type;
    private String value;
    private int id;

    public Token(TokenClass type, String value, int id) {
        this.type = type;
        this.value = value;
        this.id = id;
    }

    public TokenClass getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    public int getID() {
        return id;
    }
}
