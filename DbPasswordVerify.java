import java.sql.*;
import com.dreamspace.api.service.PasswordHashing;
public class DbPasswordVerify {
  public static void main(String[] args) throws Exception {
    try (Connection c=DriverManager.getConnection("jdbc:postgresql://43.155.160.15:5432/dream_space","dream_space","123456");
         PreparedStatement p=c.prepareStatement("SELECT \"id\",\"phone\",\"passwordHash\",\"status\" FROM \"User\" WHERE \"phone\"=?")) {
      p.setString(1,"18096634595");
      try (ResultSet r=p.executeQuery()) {
        if (!r.next()) throw new IllegalStateException("user not found");
        String hash=r.getString("passwordHash");
        System.out.println("id="+r.getString("id"));
        System.out.println("status="+r.getString("status"));
        System.out.println("hashPrefix="+(hash == null ? "null" : hash.substring(0, Math.min(hash.indexOf('$') < 0 ? hash.length() : hash.indexOf('$'), 40))));
        System.out.println("hashParts="+(hash == null ? -1 : hash.split("\\$",-1).length));
        System.out.println("matchesProjectPasswordHashing="+PasswordHashing.matches("12345678", hash));
        System.out.println("rejectsWrongPassword="+(!PasswordHashing.matches("123456789", hash)));
      }
    }
  }
}
