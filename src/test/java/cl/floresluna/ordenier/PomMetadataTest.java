package cl.floresluna.ordenier;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

class PomMetadataTest {

	private static Element project;

	@BeforeAll
	static void loadPom() throws Exception {
		Document pom = DocumentBuilderFactory.newInstance().newDocumentBuilder()
				.parse(Path.of("pom.xml").toFile());
		project = pom.getDocumentElement();
	}

	private static Element child(Element parent, String name) {
		NodeList children = parent.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			Node node = children.item(i);
			if (node instanceof Element element && element.getTagName().equals(name)) {
				return element;
			}
		}
		return null;
	}

	/** Etiquetas sin contenido: sin hijos y sin texto, dentro de un elemento. */
	private static List<String> emptyTags(Element element, String path) {
		List<String> empty = new ArrayList<>();
		String current = path + "/" + element.getTagName();
		boolean hasChildElements = false;
		NodeList children = element.getChildNodes();
		for (int i = 0; i < children.getLength(); i++) {
			if (children.item(i) instanceof Element childElement) {
				hasChildElements = true;
				empty.addAll(emptyTags(childElement, current));
			}
		}
		if (!hasChildElements && element.getTextContent().isBlank()) {
			empty.add(current);
		}
		return empty;
	}

	private static void assertHasContent(String tag) {
		Element element = child(project, tag);
		assertThat(element).as("<%s> debe existir", tag).isNotNull();
		assertThat(emptyTags(element, "")).as("<%s> no debe tener etiquetas vacias", tag).isEmpty();
	}

	@Test
	void nameHasContent() {
		assertHasContent("name");
	}

	@Test
	void descriptionHasContent() {
		assertHasContent("description");
	}

	@Test
	void urlHasContent() {
		assertHasContent("url");
	}

	@Test
	void developersHaveContent() {
		assertHasContent("developers");
	}

	@Test
	void scmHasContent() {
		assertHasContent("scm");
	}

	@Test
	void licensesDoesNotExistEmpty() {
		Element licenses = child(project, "licenses");
		if (licenses != null) {
			assertThat(emptyTags(licenses, "")).as("<licenses> no debe tener etiquetas vacias").isEmpty();
		}
	}
}
