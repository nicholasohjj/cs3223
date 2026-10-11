package simpledb.parse;
import java.util.Scanner;

// Will successfully read in lines of text denoting an
// SQL expression of the form "id op c" or "c op id",
// where op is one of =, <, <=, >, >=, != or <>.

public class LexerTest {
	public static void main(String[] args) {
		Scanner sc = new Scanner(System.in);
		while (sc.hasNext()) {
			String s = sc.nextLine();
			Lexer lex = new Lexer(s);
			String x, opr, out; int y;
			if (lex.matchId()) {
				x = lex.eatId();
				opr = lex.eatOpr();
				y = lex.eatIntConstant();
				out = x + " " + opr + " " + y;
			}
			else {
				y = lex.eatIntConstant();
				opr = lex.eatOpr();
				x = lex.eatId();	
				out = y + " " + opr + " " + x;
			}
			System.out.println(out);
		}
		sc.close();
	}
}
