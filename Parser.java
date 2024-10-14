package COS341COCO;
//using recursive descent as the parsing strat. parser is creating AST (abstract syntax tree) as it parses the input, where each node in the AST represents a construct in the language.
import java.util.ArrayList;
import java.util.List;

//havent implemented error handling

enum TokenType {
    IDENTIFIER, INTEGER, FLOAT, STRING,
    PLUS, MINUS, MULTIPLY, DIVIDE, ASSIGN,
    LPAREN, RPAREN, LBRACE, RBRACE,
    IF, ELSE, WHILE, FOR, RETURN,
    SEMICOLON, COMMA,
    EOF
}

class Token {
    TokenType type;
    String value;

    Token(TokenType type, String value) {
        this.type = type;
        this.value = value;
    }
}

class ASTNode {
    //base class empty as it is curr just a basic structural element
}

class Parser {
    private List<Token> tokens;
    private int currentTokenIndex;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.currentTokenIndex = 0;
    }

    private Token getCurrentToken() {
        return tokens.get(currentTokenIndex);
    }

    private void advance() {
        currentTokenIndex++;
    }

    private boolean match(TokenType type) {
        if (getCurrentToken().type == type) {
            advance();
            return true;
        }
        return false;
    }

    private void expect(TokenType type) {
        if (!match(type)) {
            throw new RuntimeException("Expected " + type + " but got " + getCurrentToken().type);
        }
    }

    public ASTNode parse() {
        return parseProgram();
    }

    private ASTNode parseProgram() {
        List<ASTNode> statements = new ArrayList<>();
        while (getCurrentToken().type != TokenType.EOF) {
            statements.add(parseStatement());
        }
        return new ProgramNode(statements);
    }

    private ASTNode parseStatement() {
        switch (getCurrentToken().type) {
            case IF:
                return parseIfStatement();
            case WHILE:
                return parseWhileStatement();
            case FOR:
                return parseForStatement();
            case RETURN:
                return parseReturnStatement();
            case LBRACE:
                return parseBlock();
            default:
                return parseExpressionStatement();
        }
    }

    private ASTNode parseIfStatement() {
        expect(TokenType.IF);
        expect(TokenType.LPAREN);
        ASTNode condition = parseExpression();
        expect(TokenType.RPAREN);
        ASTNode thenBranch = parseStatement();
        ASTNode elseBranch = null;
        if (match(TokenType.ELSE)) {
            elseBranch = parseStatement();
        }
        return new IfNode(condition, thenBranch, elseBranch);
    }

    private ASTNode parseWhileStatement() {
        expect(TokenType.WHILE);
        expect(TokenType.LPAREN);
        ASTNode condition = parseExpression();
        expect(TokenType.RPAREN);
        ASTNode body = parseStatement();
        return new WhileNode(condition, body);
    }

    private ASTNode parseForStatement() {
        expect(TokenType.FOR);
        expect(TokenType.LPAREN);
        ASTNode initialization = parseExpressionStatement();
        ASTNode condition = parseExpression();
        expect(TokenType.SEMICOLON);
        ASTNode increment = parseExpression();
        expect(TokenType.RPAREN);
        ASTNode body = parseStatement();
        return new ForNode(initialization, condition, increment, body);
    }

    private ASTNode parseReturnStatement() {
        expect(TokenType.RETURN);
        ASTNode value = parseExpression();
        expect(TokenType.SEMICOLON);
        return new ReturnNode(value);
    }

    private ASTNode parseBlock() {
        expect(TokenType.LBRACE);
        List<ASTNode> statements = new ArrayList<>();
        while (!match(TokenType.RBRACE)) {
            statements.add(parseStatement());
        }
        return new BlockNode(statements);
    }

    private ASTNode parseExpressionStatement() {
        ASTNode expr = parseExpression();
        expect(TokenType.SEMICOLON);
        return new ExpressionStatementNode(expr);
    }

    private ASTNode parseExpression() {
        return parseAssignment();
    }

    private ASTNode parseAssignment() {
        ASTNode left = parseAdditiveExpression();
        if (match(TokenType.ASSIGN)) {
            ASTNode right = parseAssignment();
            return new AssignmentNode(left, right);
        }
        return left;
    }

    private ASTNode parseAdditiveExpression() {
        ASTNode left = parseMultiplicativeExpression();
        while (match(TokenType.PLUS) || match(TokenType.MINUS)) {
            Token operator = tokens.get(currentTokenIndex - 1);
            ASTNode right = parseMultiplicativeExpression();
            left = new BinaryOpNode(left, operator, right);
        }
        return left;
    }

    private ASTNode parseMultiplicativeExpression() {
        ASTNode left = parsePrimaryExpression();
        while (match(TokenType.MULTIPLY) || match(TokenType.DIVIDE)) {
            Token operator = tokens.get(currentTokenIndex - 1);
            ASTNode right = parsePrimaryExpression();
            left = new BinaryOpNode(left, operator, right);
        }
        return left;
    }

    private ASTNode parsePrimaryExpression() {
        if (match(TokenType.IDENTIFIER)) {
            return new IdentifierNode(tokens.get(currentTokenIndex - 1).value);
        } else if (match(TokenType.INTEGER)) {
            return new IntegerNode(Integer.parseInt(tokens.get(currentTokenIndex - 1).value));
        } else if (match(TokenType.FLOAT)) {
            return new FloatNode(Float.parseFloat(tokens.get(currentTokenIndex - 1).value));
        } else if (match(TokenType.STRING)) {
            return new StringNode(tokens.get(currentTokenIndex - 1).value);
        } else if (match(TokenType.LPAREN)) {
            ASTNode expr = parseExpression();
            expect(TokenType.RPAREN);
            return expr;
        }
        throw new RuntimeException("Unexpected token: " + getCurrentToken().type);
    }
}

// AST node classes (simplified for brevity)
class ProgramNode extends ASTNode {
    List<ASTNode> statements;
    ProgramNode(List<ASTNode> statements) { this.statements = statements; }
}

class IfNode extends ASTNode {
    ASTNode condition, thenBranch, elseBranch;
    IfNode(ASTNode condition, ASTNode thenBranch, ASTNode elseBranch) {
        this.condition = condition;
        this.thenBranch = thenBranch;
        this.elseBranch = elseBranch;
    }
}

class WhileNode extends ASTNode {
    ASTNode condition, body;
    WhileNode(ASTNode condition, ASTNode body) {
        this.condition = condition;
        this.body = body;
    }
}

class ForNode extends ASTNode {
    ASTNode initialization, condition, increment, body;
    ForNode(ASTNode initialization, ASTNode condition, ASTNode increment, ASTNode body) {
        this.initialization = initialization;
        this.condition = condition;
        this.increment = increment;
        this.body = body;
    }
}

class ReturnNode extends ASTNode {
    ASTNode value;
    ReturnNode(ASTNode value) { this.value = value; }
}

class BlockNode extends ASTNode {
    List<ASTNode> statements;
    BlockNode(List<ASTNode> statements) { this.statements = statements; }
}

class ExpressionStatementNode extends ASTNode {
    ASTNode expression;
    ExpressionStatementNode(ASTNode expression) { this.expression = expression; }
}

class AssignmentNode extends ASTNode {
    ASTNode left, right;
    AssignmentNode(ASTNode left, ASTNode right) {
        this.left = left;
        this.right = right;
    }
}

class BinaryOpNode extends ASTNode {
    ASTNode left, right;
    Token operator;
    BinaryOpNode(ASTNode left, Token operator, ASTNode right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }
}

class IdentifierNode extends ASTNode {
    String name;
    IdentifierNode(String name) { this.name = name; }
}

class IntegerNode extends ASTNode {
    int value;
    IntegerNode(int value) { this.value = value; }
}

class FloatNode extends ASTNode {
    float value;
    FloatNode(float value) { this.value = value; }
}

class StringNode extends ASTNode {
    String value;
    StringNode(String value) { this.value = value; }
}
