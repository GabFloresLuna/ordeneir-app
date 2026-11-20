package cl.floresluna.ordenier.category;

import org.springframework.jdbc.core.JdbcTemplate;

/** Ayudas de prueba para categorías: ids de predefinidas y categorías propias por SQL (plan, estrategia de tests). */
public final class CategoryTestSupport {

	private CategoryTestSupport() {
	}

	public static long predefinedId(JdbcTemplate jdbc, String name, String type) {
		return jdbc.queryForObject(
				"SELECT id FROM categories WHERE user_id IS NULL AND name = ? AND type = ?",
				Long.class, name, type);
	}

	public static long othersId(JdbcTemplate jdbc, String type) {
		return predefinedId(jdbc, "Otros", type);
	}

	/** Fragmento JSON `"categoryId":N,` con "Otros" del tipo dado; para un tipo inválido usa el de GASTO. */
	public static String categoryField(JdbcTemplate jdbc, String type) {
		String validType = "INGRESO".equals(type) ? "INGRESO" : "GASTO";
		return "\"categoryId\":" + othersId(jdbc, validType) + ",";
	}

	public static long insertOwn(JdbcTemplate jdbc, long userId, String name, String type) {
		return jdbc.queryForObject(
				"INSERT INTO categories (user_id, name, type) VALUES (?, ?, ?) RETURNING id",
				Long.class, userId, name, type);
	}

	public static void markDeleted(JdbcTemplate jdbc, long categoryId) {
		jdbc.update("UPDATE categories SET deleted_at = now() WHERE id = ?", categoryId);
	}
}
