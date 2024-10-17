import org.w3c.dom.*;
import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;
import java.io.*;
import java.util.*;

public class Parser {
    private List<Token> tokens;
    private int currentTokenIndex;
    private Stack<Integer> stateStack;
    private Stack<TreeNode> nodeStack;
    private Map<Integer, Map<String, Action>> actionTable;
    private Map<Integer, Map<String, Integer>> gotoTable;
    private int nextUniqueId = 1;

    private String[] parseCSVLine(String line) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder currentField = new StringBuilder();
        
        for (char c : line.toCharArray()) {
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ';' && !inQuotes) {
                result.add(currentField.toString().trim());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        result.add(currentField.toString().trim());
        
        return result.toArray(new String[0]);
    }

    public Parser(String inputXmlFile) {
        this.tokens = readTokensFromXml(inputXmlFile);
        this.currentTokenIndex = 0;
        this.stateStack = new Stack<>();
        this.nodeStack = new Stack<>();
        initializeTables();
    }

    private List<Token> readTokensFromXml(String inputXmlFile) {
        List<Token> tokens = new ArrayList<>();
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputXmlFile);
            doc.getDocumentElement().normalize();

            NodeList tokenList = doc.getElementsByTagName("TOK");
            for (int i = 0; i < tokenList.getLength(); i++) {
                Element tokenElement = (Element) tokenList.item(i);
                int id = Integer.parseInt(tokenElement.getElementsByTagName("ID").item(0).getTextContent());
                TokenClass type = TokenClass.valueOf(tokenElement.getElementsByTagName("CLASS").item(0).getTextContent());
                String value = tokenElement.getElementsByTagName("WORD").item(0).getTextContent();
                tokens.add(new Token(type, value, id));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tokens;
    }
    private void initializeTables() {
        actionTable = new HashMap<>();
        gotoTable = new HashMap<>();
    
        Set<String> nonTerminals = new HashSet<>(Arrays.asList(
            "E'", "PROG", "GLOBVARS", "VTYP", "VNAME", "ALGO", "INSTRUC", "COMMAND", 
            "ATOMIC", "CONST", "ASSIGN", "CALL", "BRANCH", "TERM", "OP", "ARG", 
            "COND", "SIMPLE", "COMPOSIT", "UNOP", "BINOP", "FNAME", "FUNCTIONS", 
            "DECL", "HEADER", "FTYP", "BODY", "PROLOG", "EPILOG", "LOCVARS", "SUBFUNCS"
        ));
    
        try (BufferedReader br = new BufferedReader(new FileReader("Parser/parsetable.csv"))) {
            String line;
            String[] headers = null;
            int stateNumber = 0;
    
            while ((line = br.readLine()) != null) {
                String[] values = parseCSVLine(line);
                
                if (headers == null) {
                    headers = values;
                    continue;
                }
    
                actionTable.put(stateNumber, new HashMap<>());
                gotoTable.put(stateNumber, new HashMap<>());
    
                for (int i = 1; i < values.length; i++) {
                    String value = values[i].trim();
                    if (!value.isEmpty()) {
                        if (nonTerminals.contains(headers[i])) {
                            // This is a goto entry
                            gotoTable.get(stateNumber).put(headers[i], Integer.parseInt(value));
                        } else if (value.startsWith("s")) {
                            // Shift action
                            actionTable.get(stateNumber).put(headers[i], new Action(Action.Type.SHIFT, Integer.parseInt(value.substring(1))));
                        } else if (value.startsWith("r")) {
                            // Reduce action
                            actionTable.get(stateNumber).put(headers[i], new Action(Action.Type.REDUCE, Integer.parseInt(value.substring(1))));
                        } else if (value.equals("acc")) {
                            // Accept action
                            actionTable.get(stateNumber).put(headers[i], new Action(Action.Type.ACCEPT, 0));
                        }
                    }
                }
                stateNumber++;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    
    
    public void parse() {
        stateStack.push(0);
        TreeNode root = new TreeNode(getNextUniqueId(), "E'", null);
        nodeStack.push(root);

        while (true) {
            int currentState = stateStack.peek();
            System.out.println("Current state: " + currentState);
            Token currentToken = getCurrentToken();
            System.out.println("Current token: " + currentToken);
            String currentSymbol = getSymbolFromToken(currentToken);
            System.out.println("Current symbol: " + currentSymbol);

            Action action = actionTable.get(currentState).get(currentSymbol);
            if (action == null) {
                error("No action defined for state " + currentState + " and symbol " + currentSymbol);
            }

            switch (action.type) {
                case SHIFT:
                    stateStack.push(action.value);
                    TreeNode leaf = new TreeNode(getNextUniqueId(), currentSymbol, nodeStack.peek());
                    leaf.setToken(currentToken);
                    nodeStack.peek().addChild(leaf);
                    nodeStack.push(leaf);
                    advance();
                    break;
                case REDUCE:
                    reduce(action.value);
                    break;
                case ACCEPT:
                    System.out.println("Parsing completed successfully.");
                    writeSyntaxTreeToXml(root, "syntax_tree.xml");
                    return;
                case ERROR:
                    error("Parsing error at token: " + currentToken);
            }
        }
    }

    private void reduce(int productionNumber) {
        Production production = getProduction(productionNumber);
        List<TreeNode> children = new ArrayList<>();
        for (int i = 0; i < production.rightHandSide.size(); i++) {
            stateStack.pop();
            children.add(0, nodeStack.pop());
        }
        
        TreeNode parent = nodeStack.peek();
        TreeNode newNode = new TreeNode(getNextUniqueId(), production.leftHandSide, parent);
        newNode.setChildren(children);
        for (TreeNode child : children) {
            child.setParent(newNode);
        }
        parent.addChild(newNode);
        nodeStack.push(newNode);
    
        int currentState = stateStack.peek();
        String leftHandSide = production.leftHandSide;
    
        if (!gotoTable.containsKey(currentState)) {
            error("No goto entry for state " + currentState);
        }
        
        Map<String, Integer> gotoRow = gotoTable.get(currentState);
        if (!gotoRow.containsKey(leftHandSide)) {
            error("No goto entry for non-terminal " + leftHandSide + " in state " + currentState);
        }
    
        int gotoState = gotoRow.get(leftHandSide);
        stateStack.push(gotoState);
    }
    

    private Production getProduction(int productionNumber) {
        switch (productionNumber) {
            case 0:
                return new Production("E'", Arrays.asList("PROG"));
            case 1:
                return new Production("PROG", Arrays.asList("main", "GLOBVARS", "ALGO", "FUNCTIONS"));
            case 2:
                return new Production("GLOBVARS", Arrays.asList()); // Empty production
            case 3:
                return new Production("GLOBVARS", Arrays.asList("VTYP", "VNAME", ",", "GLOBVARS"));
            case 4:
                return new Production("VTYP", Arrays.asList("num"));
            case 5:
                return new Production("VTYP", Arrays.asList("text"));
            case 6:
                return new Production("VNAME", Arrays.asList("vtoken"));
            case 7:
                return new Production("ALGO", Arrays.asList("begin", "INSTRUC", "end"));
            case 8:
                return new Production("INSTRUC", Arrays.asList()); // Empty production
            case 9:
                return new Production("INSTRUC", Arrays.asList("COMMAND", ";", "INSTRUC"));
            case 10:
                return new Production("COMMAND", Arrays.asList("skip"));
            case 11:
                return new Production("COMMAND", Arrays.asList("halt"));
            case 12:
                return new Production("COMMAND", Arrays.asList("print", "ATOMIC"));
            case 13:
                return new Production("COMMAND", Arrays.asList("ASSIGN"));
            case 14:
                return new Production("COMMAND", Arrays.asList("CALL"));
            case 15:
                return new Production("COMMAND", Arrays.asList("BRANCH"));
            case 16:
                return new Production("COMMAND", Arrays.asList("return", "ATOMIC"));
            case 17:
                return new Production("ATOMIC", Arrays.asList("VNAME"));
            case 18:
                return new Production("ATOMIC", Arrays.asList("CONST"));
            case 19:
                return new Production("CONST", Arrays.asList("ntoken"));
            case 20:
                return new Production("CONST", Arrays.asList("ttoken"));
            case 21:
                return new Production("ASSIGN", Arrays.asList("VNAME", "<", "input"));
            case 22:
                return new Production("ASSIGN", Arrays.asList("VNAME", "=", "TERM"));
            case 23:
                return new Production("CALL", Arrays.asList("FNAME", "(", "ATOMIC", ",", "ATOMIC", ",", "ATOMIC", ")"));
            case 24:
                return new Production("BRANCH", Arrays.asList("if", "COND", "then", "ALGO", "else", "ALGO"));
            case 25:
                return new Production("TERM", Arrays.asList("ATOMIC"));
            case 26:
                return new Production("TERM", Arrays.asList("CALL"));
            case 27:
                return new Production("TERM", Arrays.asList("OP"));
            case 28:
                return new Production("OP", Arrays.asList("UNOP", "(", "ARG", ")"));
            case 29:
                return new Production("OP", Arrays.asList("BINOP", "(", "ARG", ",", "ARG", ")"));
            case 30:
                return new Production("ARG", Arrays.asList("ATOMIC"));
            case 31:
                return new Production("ARG", Arrays.asList("OP"));
            case 32:
                return new Production("COND", Arrays.asList("SIMPLE"));
            case 33:
                return new Production("COND", Arrays.asList("COMPOSIT"));
            case 34:
                return new Production("SIMPLE", Arrays.asList("BINOP", "(", "ATOMIC", ",", "ATOMIC", ")"));
            case 35:
                return new Production("COMPOSIT", Arrays.asList("BINOP", "(", "SIMPLE", ",", "SIMPLE", ")"));
            case 36:
                return new Production("COMPOSIT", Arrays.asList("UNOP", "(", "SIMPLE", ")"));
            case 37:
                return new Production("UNOP", Arrays.asList("not"));
            case 38:
                return new Production("UNOP", Arrays.asList("sqrt"));
            case 39:
                return new Production("BINOP", Arrays.asList("or"));
            case 40:
                return new Production("BINOP", Arrays.asList("and"));
            case 41:
                return new Production("BINOP", Arrays.asList("eq"));
            case 42:
                return new Production("BINOP", Arrays.asList("grt"));
            case 43:
                return new Production("BINOP", Arrays.asList("add"));
            case 44:
                return new Production("BINOP", Arrays.asList("sub"));
            case 45:
                return new Production("BINOP", Arrays.asList("mul"));
            case 46:
                return new Production("BINOP", Arrays.asList("div"));
            case 47:
                return new Production("FNAME", Arrays.asList("ftoken"));
            case 48:
                return new Production("FUNCTIONS", Arrays.asList()); // Empty production
            case 49:
                return new Production("FUNCTIONS", Arrays.asList("DECL", "FUNCTIONS"));
            case 50:
                return new Production("DECL", Arrays.asList("HEADER", "BODY"));
            case 51:
                return new Production("HEADER", Arrays.asList("FTYP", "FNAME", "(", "VNAME", ",", "VNAME", ",", "VNAME", ")"));
            case 52:
                return new Production("FTYP", Arrays.asList("num"));
            case 53:
                return new Production("FTYP", Arrays.asList("void"));
            case 54:
                return new Production("BODY", Arrays.asList("PROLOG", "LOCVARS", "ALGO", "EPILOG", "SUBFUNCS", "end"));
            case 55:
                return new Production("PROLOG", Arrays.asList("{"));
            case 56:
                return new Production("EPILOG", Arrays.asList("}"));
            case 57:
                return new Production("LOCVARS", Arrays.asList("VTYP", "VNAME", ",", "VTYP", "VNAME", ",", "VTYP", "VNAME", ","));
            case 58:
                return new Production("SUBFUNCS", Arrays.asList("FUNCTIONS"));
            default:
                throw new RuntimeException("Unknown production number: " + productionNumber);
        }
    }    

    private Token getCurrentToken() {
        if (currentTokenIndex >= tokens.size()) {
            return new Token(TokenClass.EOF, "$", tokens.size());
        }
        return tokens.get(currentTokenIndex);
    }

    private String getSymbolFromToken(Token token) {
        if (token.getType() == TokenClass.EOF) {
            return "$";
        }
        else if (token.getType() == TokenClass.VARIABLE_NAME) {
            return "vtoken";
        }
        else if (token.getType() == TokenClass.NUMBER) {
            return "ntoken";
        }
        else if (token.getType() == TokenClass.TEXT) {
            return "ttoken";
        }
        else if (token.getType() == TokenClass.FUNCTION_NAME) {
            return "ftoken";
        }
        else if (token.getType() == TokenClass.KEYWORD || token.getType() == TokenClass.DELIMITER || token.getType() == TokenClass.OPERATOR) {
            return token.getValue();
        } else {
            return token.getType().toString();
        }
    }

    private void advance() {
        currentTokenIndex++;
    }

    private void error(String message) {
        throw new RuntimeException("Parse error: " + message);
    }

    private int getNextUniqueId() {
        return nextUniqueId++;
    }

    private void writeSyntaxTreeToXml(TreeNode root, String outputFile) {
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();

            Element rootElement = doc.createElement("SYNTREE");
            doc.appendChild(rootElement);

            Element rootNodeElement = createNodeElement(doc, root, true);
            rootElement.appendChild(rootNodeElement);

            Element innerNodesElement = doc.createElement("INNERNODES");
            rootElement.appendChild(innerNodesElement);

            Element leafNodesElement = doc.createElement("LEAFNODES");
            rootElement.appendChild(leafNodesElement);

            writeNodesRecursively(doc, root, innerNodesElement, leafNodesElement);

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(new File(outputFile));
            transformer.transform(source, result);

            System.out.println("Syntax tree XML file saved!");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void writeNodesRecursively(Document doc, TreeNode node, Element innerNodesElement, Element leafNodesElement) {
        for (TreeNode child : node.getChildren()) {
            if (child.getToken() == null) {
                Element innerNode = createNodeElement(doc, child, false);
                innerNodesElement.appendChild(innerNode);
                writeNodesRecursively(doc, child, innerNodesElement, leafNodesElement);
            } else {
                Element leafNode = createLeafElement(doc, child);
                leafNodesElement.appendChild(leafNode);
            }
        }
    }

    private Element createNodeElement(Document doc, TreeNode node, boolean isRoot) {
        Element nodeElement = doc.createElement(isRoot ? "ROOT" : "IN");

        Element unidElement = doc.createElement("UNID");
        unidElement.setTextContent(String.valueOf(node.getId()));
        nodeElement.appendChild(unidElement);

        Element symbElement = doc.createElement("SYMB");
        symbElement.setTextContent(node.getSymbol());
        nodeElement.appendChild(symbElement);

        if (!isRoot) {
            Element parentElement = doc.createElement("PARENT");
            parentElement.setTextContent(String.valueOf(node.getParent().getId()));
            nodeElement.appendChild(parentElement);
        }

        Element childrenElement = doc.createElement("CHILDREN");
        for (TreeNode child : node.getChildren()) {
            Element idElement = doc.createElement("ID");
            idElement.setTextContent(String.valueOf(child.getId()));
            childrenElement.appendChild(idElement);
        }
        nodeElement.appendChild(childrenElement);

        return nodeElement;
    }

    private Element createLeafElement(Document doc, TreeNode leaf) {
        Element leafElement = doc.createElement("LEAF");

        Element parentElement = doc.createElement("PARENT");
        parentElement.setTextContent(String.valueOf(leaf.getParent().getId()));
        leafElement.appendChild(parentElement);

        Element unidElement = doc.createElement("UNID");
        unidElement.setTextContent(String.valueOf(leaf.getId()));
        leafElement.appendChild(unidElement);

        Element terminalElement = doc.createElement("TERMINAL");
        Token token = leaf.getToken();
        terminalElement.setTextContent(token.getType() + ": " + token.getValue());
        leafElement.appendChild(terminalElement);

        return leafElement;
    }
    public static void main(String[] args) {
        Parser parser = new Parser("output.xml");
        parser.parse();
    }

    private static class TreeNode {
        private int id;
        private String symbol;
        private TreeNode parent;
        private List<TreeNode> children;
        private Token token;

        public TreeNode(int id, String symbol, TreeNode parent) {
            this.id = id;
            this.symbol = symbol;
            this.parent = parent;
            this.children = new ArrayList<>();
        }

        int getId() {
            return id;
        }
        String getSymbol() {
            return symbol;
        }
        TreeNode getParent() {
            return parent;
        }
        List<TreeNode> getChildren() {
            return children;
        }
        Token getToken() {
            return token;
        }
        void setToken(Token token) {
            this.token = token;
        }
        void setChildren(List<TreeNode> children) {
            this.children = children;
        }
        void addChild(TreeNode child) {
            children.add(child);
        }
        void setParent(TreeNode parent) {
            this.parent = parent;
        }
    }

    private static class Production {
        String leftHandSide;
        List<String> rightHandSide;
    
        public Production(String lhs, List<String> rhs) {
            this.leftHandSide = lhs;
            this.rightHandSide = rhs;
        }
    
        @Override
        public String toString() {
            return leftHandSide + " -> " + String.join(" ", rightHandSide);
        }
    }
    

    private static class Action {
        enum Type { SHIFT, REDUCE, ACCEPT, ERROR }
        Type type;
        int value;

        Action(Type type, int value) {
            this.type = type;
            this.value = value;
        }
    }
}
