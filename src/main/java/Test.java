import java.util.function.Function;

public class Test {

	public static void main(String[] args) {
		System.out.println("test it");
		
		
		Function<Double, Double> x2 = (value) -> value*2;
		Function<Double, Double> x4 = (value) -> value*4;
		System.out.println(String.valueOf(x2.apply(3.14)));
		System.out.println(String.valueOf(x4.apply(3.14)));
		Function<Double, Double> x2composex4 = x2.compose(x4);
		
		System.out.println(String.valueOf(x2composex4.apply(3.14)));
		
		Function<Double, Double> x2thenx4 = x2.andThen(x4);
		
		System.out.println(String.valueOf(x2thenx4.apply(3.14)));
		
		
		/*
		 * Function<Double, Double> logThenSqrt = sqrt.compose(log);
		 * System.out.println(String.valueOf(logThenSqrt.apply(3.14))); // Output: 1.06
		 * Function<Double, Double> sqrtThenLog = sqrt.andThen(log);
		 * System.out.println(String.valueOf(sqrtThenLog.apply(3.14)));
		 */
		
	

	}

}
