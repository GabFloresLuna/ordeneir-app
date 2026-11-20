package cl.floresluna.ordenier.common;

import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;

/** Rango de un mes `AAAA-MM` en hora de Santiago, como [from, to) (plan 003 D10). */
public record MonthRange(Instant from, Instant to) {

	private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");
	private static final ZoneId SANTIAGO = ZoneId.of("America/Santiago");

	/** Un mes mal formado es `DATOS_INVALIDOS`. */
	public static MonthRange parse(String month) {
		if (month == null || !MONTH_PATTERN.matcher(month).matches()) {
			throw new InvalidDataException();
		}
		return of(YearMonth.parse(month));
	}

	/** Mes al que pertenece un instante, en hora de Santiago. */
	public static MonthRange containing(Instant instant) {
		return of(YearMonth.from(instant.atZone(SANTIAGO)));
	}

	/** Mes `AAAA-MM` al que pertenece un instante, en hora de Santiago. */
	public static String monthOf(Instant instant) {
		return YearMonth.from(instant.atZone(SANTIAGO)).toString();
	}

	private static MonthRange of(YearMonth month) {
		return new MonthRange(month.atDay(1).atStartOfDay(SANTIAGO).toInstant(),
				month.plusMonths(1).atDay(1).atStartOfDay(SANTIAGO).toInstant());
	}
}
