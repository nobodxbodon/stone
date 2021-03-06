package stone;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import stone.ast.语法树类;
import stone.ast.语法树叶类;
import stone.ast.ASTList;

public class Parser {
    protected static abstract class Element {
        protected abstract void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外;
        protected abstract boolean match(词法分析器类 lexer) throws 分析例外;
    }

    protected static class Tree extends Element {
        protected Parser parser;
        protected Tree(Parser p) { parser = p; }
        protected void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外
        {
            res.add(parser.parse(lexer));
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 { 
            return parser.match(lexer);
        }
    }

    protected static class OrTree extends Element {
        protected Parser[] parsers;
        protected OrTree(Parser[] p) { parsers = p; }
        protected void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外
        {
            Parser p = choose(lexer);
            if (p == null)
                throw new 分析例外(lexer.瞄(0));
            else
                res.add(p.parse(lexer));
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 {
            return choose(lexer) != null;
        }
        protected Parser choose(词法分析器类 lexer) throws 分析例外 {
            for (Parser p: parsers)
                if (p.match(lexer))
                    return p;

            return null;
        }
        protected void insert(Parser p) {
            Parser[] newParsers = new Parser[parsers.length + 1];
            newParsers[0] = p;
            System.arraycopy(parsers, 0, newParsers, 1, parsers.length);
            parsers = newParsers;
        }
    }

    protected static class Repeat extends Element {
        protected Parser parser;
        protected boolean onlyOnce;
        protected Repeat(Parser p, boolean once) { parser = p; onlyOnce = once; }
        protected void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外
        {
            while (parser.match(lexer)) {
                语法树类 t = parser.parse(lexer);
                if (t.getClass() != ASTList.class || t.子个数() > 0)
                    res.add(t);
                if (onlyOnce)
                    break;
            }
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 {
            return parser.match(lexer);
        }
    }

    protected static abstract class AToken extends Element {
        protected Factory factory;
        protected AToken(Class<? extends 语法树叶类> type) {
            if (type == null)
                type = 语法树叶类.class;
            factory = Factory.get(type, 词类.class);
        }
        protected void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外
        {
            词类 t = lexer.读();
            if (test(t)) {
                语法树类 leaf = factory.make(t);
                res.add(leaf);
            }
            else
                throw new 分析例外(t);
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 {
            return test(lexer.瞄(0));
        }
        protected abstract boolean test(词类 t); 
    }

    protected static class IdToken extends AToken {
        HashSet<String> reserved;
        protected IdToken(Class<? extends 语法树叶类> type, HashSet<String> r) {
            super(type);
            reserved = r != null ? r : new HashSet<String>();
        }
        protected boolean test(词类 t) {
            return t.为标识符() && !reserved.contains(t.取文本());
        }
    }

    protected static class NumToken extends AToken {
        protected NumToken(Class<? extends 语法树叶类> type) { super(type); }
        protected boolean test(词类 t) { return t.为数(); }
    }

    protected static class StrToken extends AToken {
        protected StrToken(Class<? extends 语法树叶类> type) { super(type); }
        protected boolean test(词类 t) { return t.isString(); }
    }

    protected static class Leaf extends Element {
        protected String[] tokens;
        protected Leaf(String[] pat) { tokens = pat; }
        protected void parse(词法分析器类 lexer, List<语法树类> res)
            throws 分析例外
        {
            词类 t = lexer.读();
            if (t.为标识符())
                for (String token: tokens)
                    if (token.equals(t.取文本())) {
                        find(res, t);
                        return;
                    }

            if (tokens.length > 0)
                throw new 分析例外(tokens[0] + " expected.", t);
            else
                throw new 分析例外(t);
        }
        protected void find(List<语法树类> res, 词类 t) {
            res.add(new 语法树叶类(t));
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 {
            词类 t = lexer.瞄(0);
            if (t.为标识符())
                for (String token: tokens)
                    if (token.equals(t.取文本()))
                        return true;

            return false;
        }
    }

    protected static class Skip extends Leaf {
        protected Skip(String[] t) { super(t); }
        protected void find(List<语法树类> res, 词类 t) {}
    }

    public static class Precedence {
        int value;
        boolean leftAssoc; // left associative
        public Precedence(int v, boolean a) {
            value = v; leftAssoc = a;
        }
    }

    public static class Operators extends HashMap<String,Precedence> {
        public static boolean LEFT = true;
        public static boolean RIGHT = false;
        public void add(String name, int prec, boolean leftAssoc) {
            put(name, new Precedence(prec, leftAssoc));
        }
    }

    protected static class Expr extends Element {
        protected Factory factory;
        protected Operators ops;
        protected Parser factor;
        protected Expr(Class<? extends 语法树类> clazz, Parser exp,
                       Operators map)
        {
            factory = Factory.getForASTList(clazz);
            ops = map;
            factor = exp;
        }
        public void parse(词法分析器类 lexer, List<语法树类> res) throws 分析例外 {
            语法树类 right = factor.parse(lexer);
            Precedence prec;
            while ((prec = nextOperator(lexer)) != null)
                right = doShift(lexer, right, prec.value);

            res.add(right);
        }
        private 语法树类 doShift(词法分析器类 lexer, 语法树类 left, int prec)
            throws 分析例外
        {
            ArrayList<语法树类> list = new ArrayList<语法树类>();
            list.add(left);
            list.add(new 语法树叶类(lexer.读()));
            语法树类 right = factor.parse(lexer);
            Precedence next;
            while ((next = nextOperator(lexer)) != null
                   && rightIsExpr(prec, next))
                right = doShift(lexer, right, next.value);

            list.add(right);
            return factory.make(list);
        }
        private Precedence nextOperator(词法分析器类 lexer) throws 分析例外 {
            词类 t = lexer.瞄(0);
            if (t.为标识符())
                return ops.get(t.取文本());
            else
                return null;
        }
        private static boolean rightIsExpr(int prec, Precedence nextPrec) {
            if (nextPrec.leftAssoc)
                return prec < nextPrec.value;
            else
                return prec <= nextPrec.value;
        }
        protected boolean match(词法分析器类 lexer) throws 分析例外 {
            return factor.match(lexer);
        }
    }

    public static final String factoryName = "create";
    protected static abstract class Factory {
        protected abstract 语法树类 make0(Object arg) throws Exception;
        protected 语法树类 make(Object arg) {
            try {
                return make0(arg);
            } catch (IllegalArgumentException e1) {
                throw e1;
            } catch (Exception e2) {
                throw new RuntimeException(e2); // this compiler is broken.
            }
        }
        protected static Factory getForASTList(Class<? extends 语法树类> clazz) {
            Factory f = get(clazz, List.class);
            if (f == null)
                f = new Factory() {
                    protected 语法树类 make0(Object arg) throws Exception {
                        List<语法树类> results = (List<语法树类>)arg;
                        if (results.size() == 1)
                            return results.get(0);
                        else
                            return new ASTList(results);
                    }
                };
            return f;
        }
        protected static Factory get(Class<? extends 语法树类> clazz,
                                     Class<?> argType)
        {
            if (clazz == null)
                return null;
            try {
                final Method m = clazz.getMethod(factoryName,
                                                 new Class<?>[] { argType });
                return new Factory() {
                    protected 语法树类 make0(Object arg) throws Exception {
                        return (语法树类)m.invoke(null, arg);
                    }
                };
            } catch (NoSuchMethodException e) {}
            try {
                final Constructor<? extends 语法树类> c
                    = clazz.getConstructor(argType);
                return new Factory() {
                    protected 语法树类 make0(Object arg) throws Exception {
                        return c.newInstance(arg);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected List<Element> elements;
    protected Factory factory;

    public Parser(Class<? extends 语法树类> clazz) {
        reset(clazz);
    }
    protected Parser(Parser p) {
        elements = p.elements;
        factory = p.factory;
    }
    public 语法树类 parse(词法分析器类 lexer) throws 分析例外 {
        ArrayList<语法树类> results = new ArrayList<语法树类>();
        for (Element e: elements)
            e.parse(lexer, results);

        return factory.make(results);
    }
    protected boolean match(词法分析器类 lexer) throws 分析例外 {
        if (elements.size() == 0)
            return true;
        else {
            Element e = elements.get(0);
            return e.match(lexer);
        }
    }
    public static Parser rule() { return rule(null); }
    public static Parser rule(Class<? extends 语法树类> clazz) {
        return new Parser(clazz);
    }
    public Parser reset() {
        elements = new ArrayList<Element>();
        return this;
    }
    public Parser reset(Class<? extends 语法树类> clazz) {
        elements = new ArrayList<Element>();
        factory = Factory.getForASTList(clazz);
        return this;
    }
    public Parser number() {
        return number(null);
    }
    public Parser number(Class<? extends 语法树叶类> clazz) {
        elements.add(new NumToken(clazz));
        return this;
    }
    public Parser identifier(HashSet<String> reserved) {
        return identifier(null, reserved);
    }
    public Parser identifier(Class<? extends 语法树叶类> clazz,
                             HashSet<String> reserved)
    {
        elements.add(new IdToken(clazz, reserved));
        return this;
    }
    public Parser string() {
        return string(null);
    }
    public Parser string(Class<? extends 语法树叶类> clazz) {
        elements.add(new StrToken(clazz));
        return this;
    }
    public Parser token(String... pat) {
        elements.add(new Leaf(pat));
        return this;
    }
    public Parser sep(String... pat) {
        elements.add(new Skip(pat));
        return this;
    }
    public Parser ast(Parser p) {
        elements.add(new Tree(p));
        return this;
    }
    public Parser or(Parser... p) {
        elements.add(new OrTree(p));
        return this;
    }
    public Parser maybe(Parser p) {
        Parser p2 = new Parser(p);
        p2.reset();
        elements.add(new OrTree(new Parser[] { p, p2 }));
        return this;
    }
    public Parser option(Parser p) {
        elements.add(new Repeat(p, true));
        return this;
    }
    public Parser repeat(Parser p) {
        elements.add(new Repeat(p, false));
        return this;
    }
    public Parser expression(Parser subexp, Operators operators) {
        elements.add(new Expr(null, subexp, operators));
        return this;
    }
    public Parser expression(Class<? extends 语法树类> clazz, Parser subexp,
                             Operators operators) {
        elements.add(new Expr(clazz, subexp, operators));
        return this;
    }
    public Parser insertChoice(Parser p) {
        Element e = elements.get(0);
        if (e instanceof OrTree)
            ((OrTree)e).insert(p);
        else {
            Parser otherwise = new Parser(this);
            reset(null);
            or(p, otherwise);
        }
        return this;
    }
}
