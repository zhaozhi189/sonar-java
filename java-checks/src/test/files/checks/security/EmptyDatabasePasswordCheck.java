import java.sql.DriverManager;
import java.sql.SQLException;

class S2115 {
  void foo() throws SQLException {
    DriverManager.getConnection("jdbc:derby:memory:myDB;create=true", "AppLogin", ""); // Noncompliant
    DriverManager.getConnection("jdbc:derby:memory:myDB;create=true", "AppLogin", "Foo");

    String pwd = "";
    DriverManager.getConnection("jdbc:derby:memory:myDB;create=true", "AppLogin", pwd); // Noncompliant

    String pwd2 = "foo";
    DriverManager.getConnection("jdbc:derby:memory:myDB;create=true", "AppLogin", pwd2);

    String pRef = "";
    String pwd3 = pRef;

    DriverManager.getConnection("jdbc:derby:memory:myDB;create=true", "AppLogin", pwd3); // Noncompliant
    DriverManager.getConnection("jdbc:db2://myhost:5021/mydb:user=dbadm;password=foo");
  }
}
