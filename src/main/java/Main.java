import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ua.com.smiddle.common.model.Group;
import ua.com.smiddle.common.model.Role;
import ua.com.smiddle.common.model.User;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.Map;

/**
 * @author srg on 08.11.16.
 * @project ExcelExporter
 */
public class Main {

    private static PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);

    public static void main(String[] args) {
        try {
            System.out.println("STARTED");
            validate(args);
            EntityManagerFactory emf = Persistence.createEntityManagerFactory("Smiddle");
            EntityManager em = emf.createEntityManager();
            Map<String, Group> groups = getGroups(em);
            Map<String, Role> roles = getRole(em);
            List<User> users = getEmptyUser(groups, roles, args[0]);
            saveUserPasswordToFile(users, args[1]);
            persister(em, users);
            validateDB(em, users,args[1]);
            em.close();
            emf.close();
            System.out.println("DONE");
        } catch (ExporterException e) {
            e.printStackTrace();
        }
    }

    private static List<User> getEmptyUser(Map<String, Group> groups, Map<String, Role> roles, String path) {
        List<User> users = new ArrayList<User>();
        String password = "pzU_UsEr_pAssw0rd";
        try {
            File myFile = new File(path);
            FileInputStream fis = new FileInputStream(myFile);
            // Finds the workbook instance for XLSX file
            XSSFWorkbook myWorkBook = new XSSFWorkbook(fis);
            // Return first sheet from the XLSX workbook
            XSSFSheet mySheet = myWorkBook.getSheetAt(1);
            Row row;
            Cell cell;
            User user;
            for (int i = 1; i <= mySheet.getLastRowNum(); i++) {
                row = mySheet.getRow(i);
                try {
                    if (row.getCell(0).getStringCellValue() == null || row.getCell(0).getStringCellValue().isEmpty())
                        throw new ExporterException("Incorrect login data from Excel");
                    user = new User();
                    user.setEnabled(true);
                    user.setDeleted(false);
                    user.setLogin(row.getCell(0).getStringCellValue());
                    user.setAgentId(row.getCell(0).getStringCellValue());
                    user.setLoginAD(row.getCell(0).getStringCellValue());
                    user.setPassword(password);
                    user.setDateCreate(new Date());
                    setNames(user, row.getCell(1).getStringCellValue());
                    defineGroup(groups, user, row.getCell(2).getStringCellValue());
                    defineRole(roles, user, null);
                    users.add(user);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return users;
    }

    private static void persister(EntityManager em, List<User> users) {
        for (Iterator<User> iterator = users.iterator(); iterator.hasNext(); ) {
            User user = iterator.next();
            try {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
                em.getTransaction().begin();
                em.merge(user);
                em.getTransaction().commit();
            } catch (Exception e) {
                em.getTransaction().rollback();
                e.printStackTrace();
            }
        }
    }

    private static void defineGroup(Map<String, Group> groups, User user, String group) throws ExporterException {
        user.setGroups(new ArrayList<Group>());
        if (group == null || group.trim().isEmpty()) {
            user.getGroups().add(groups.get("NO_GROUP"));
            return;
        }
        Group g = groups.get(group.trim());
        if (g != null) {
            user.getGroups().add(g);
        } else throw new ExporterException("No available group for " + user.getLogin());
    }

    private static void saveUserPasswordToFile(List<User> users, String path) {
        try {
            FileWriter fw = new FileWriter(path + "passwords.txt");
            for (User u : users) {
                String row = String.format("%s %s" + '\n', u.getLogin(), u.getPassword());
                fw.write(row);
            }
            fw.write('\n' + String.format("Total users: %s", users.size()));
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void defineRole(Map<String, Role> roles, User user, String role) throws ExporterException {
        user.setRoles(new ArrayList<Role>());
        if (role == null || role.isEmpty()) {
            if (roles.get("USER") == null) throw new ExporterException("Unable find role USER");
            user.getRoles().add(roles.get("USER"));
        } else {
            Role r = roles.get(role);
            if (r == null) throw new ExporterException("Unable find role=" + role);
            user.getRoles().add(r);
        }
    }

    private static void setNames(User user, String name) throws ExporterException {
        if (name == null || name.isEmpty())
            throw new ExporterException("Incorrect Fname/Pname values from Excel");
        String[] names = name.trim().split(" ");
        user.setLname(names[0]);
        if (names.length == 1) {
            user.setFname(names[0]);
        } else if (names.length == 2) {
            user.setFname(names[1]);
        } else {
            user.setFname(names[1]);
            user.setPname(names[2]);
        }
    }

    private static Map<String, Group> getGroups(EntityManager em) {
        Query query;
        query = em.createQuery("SELECT g FROM Group g", Group.class);
        em.getTransaction().begin();
        List<Group> groups = (List<Group>) query.getResultList();
        em.getTransaction().commit();
        Map<String, Group> groupMap = new HashMap<String, Group>();
        for (Iterator<Group> iterator = groups.iterator(); iterator.hasNext(); ) {
            Group next = iterator.next();
            groupMap.put(next.getName(), next);
        }
        return groupMap;
    }

    private static Map<String, Role> getRole(EntityManager em) {
        Query query;
        query = em.createQuery("SELECT g FROM Role g", Role.class);
        em.getTransaction().begin();
        List<Role> groups = (List<Role>) query.getResultList();
        em.getTransaction().commit();
        Map<String, Role> map = new HashMap<String, Role>();
        for (Iterator<Role> iterator = groups.iterator(); iterator.hasNext(); ) {
            Role next = iterator.next();
            map.put(next.getName(), next);
        }
        return map;
    }

    private static void validate(String[] args) throws ExporterException {
        if (args.length != 2) {
            throw new ExporterException("Invalid paths");
        }
        String source = args[0];
        if (source == null || source.isEmpty())
            throw new ExporterException("Empty source path");
        else {
            if (!new File(source).exists())
                throw new ExporterException(String.format("File source %s doesn't exist", source));
        }
        String target = args[1];
        if (target == null || target.isEmpty())
            throw new ExporterException("Empty target path");
    }

    private static void validateDB(EntityManager em, List<User> users, String path) {
        Query query;
        query = em.createQuery("SELECT u FROM User u", User.class);
        em.getTransaction().begin();
        List<User> usersDB = (List<User>) query.getResultList();
        em.getTransaction().commit();

        Map<String, User> usersTarget = new HashMap<String, User>();
        for (Iterator<User> iterator = usersDB.iterator(); iterator.hasNext(); ) {
            User user = iterator.next();
            usersTarget.put(user.getLogin(), user);
        }
        int success = 0;
        int error = 0;
        try {
            FileWriter fw = new FileWriter(path + "report.txt");
            for (User u : users) {
                User tmp = usersTarget.get(u.getLogin());
                if (tmp != null) {
                    fw.write(tmp.getLogin() + "- OK"+'\n');
                    success++;
                } else {
                    fw.write(tmp.getLogin() + "- NONE"+'\n');
                    error++;
                }
            }
            fw.write('\n' + String.format("Total users: %s success %s false %s", users.size(), success, error));
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
