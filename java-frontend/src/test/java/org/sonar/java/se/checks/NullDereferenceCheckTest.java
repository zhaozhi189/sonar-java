/*
 * SonarQube Java
 * Copyright (C) 2012-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.java.se.checks;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.sonar.java.se.JavaCheckVerifier;

public class NullDereferenceCheckTest {

  @Test
  public void xxx() throws IOException {
    List<File> cp = mvnClassPath("C:\\projects\\test\\project\\pom.xml");
    String filename = "C:\\projects\\test\\project\\src\\main\\java\\GetReportAlertasVelocidadZona.java";
    JavaCheckVerifier.verify(filename, new NullDereferenceCheck(), cp);
  }

  private List<File> mvnClassPath(String pomFilename) throws IOException {
    Path tempFile = null;
    try {
      tempFile = Files.createTempFile("mvnclasspath", ".txt");
      Runtime.getRuntime().exec(new String[]{"mvn", "dependency:build-classpath", "-Dmdep.outputFile=" + tempFile.toString(), "-f", pomFilename});
      String cpString = new String(Files.readAllBytes(tempFile));
      return Arrays.stream(cpString.split(";")).map(File::new).collect(Collectors.toList());
    } finally {
      if (tempFile != null) {
        Files.deleteIfExists(tempFile);
      }
    }
  }


  @Test
  public void test() {
    JavaCheckVerifier.verify("src/test/files/se/NullDereferenceCheck.java", new NullDereferenceCheck());
  }

  @Test
  public void objectsMethodsTest() {
    JavaCheckVerifier.verify("src/test/files/se/ObjectsMethodsTest.java", new NullDereferenceCheck());
  }

  @Test
  public void null_array_access() {
    JavaCheckVerifier.verify("src/test/files/se/NullArrayAccess.java", new NullDereferenceCheck());
  }

  @Test
  public void chained_method_invocation_issue_order() {
    JavaCheckVerifier.verify("src/test/files/se/MethodParamInvocationOrder.java", new NullDereferenceCheck());
  }

  @Test
  public void invocation_leading_to_NPE() {
    JavaCheckVerifier.verify("src/test/files/se/MethodInvocationLeadingToNPE.java", new NullDereferenceCheck());
  }

  @Test
  public void reporting_test() {
    JavaCheckVerifier.verify("src/test/files/se/NPE_reporting.java", new NullDereferenceCheck());
  }

  @Test
  public void ruling() {
    JavaCheckVerifier.verifyNoIssue("src/test/files/se/NPEwithZeroTests.java", new NullDereferenceCheck());
  }

  @Test
  public void test_deferred_reporting() throws Exception {
    JavaCheckVerifier.verify("src/test/files/se/NPE_deferred.java", new NullDereferenceCheck());
  }

  @Test
  public void test_npe_transitive() throws Exception {
    JavaCheckVerifier.verify("src/test/files/se/NPE_transitive.java", new NullDereferenceCheck());
  }
}
