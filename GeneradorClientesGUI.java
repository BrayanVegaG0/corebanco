import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class GeneradorClientesGUI {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GeneradorClientesGUI().createAndShowGui());
    }

    // Componentes GUI
    private JFrame frame;
    private JTextField txtCantidad;
    private JButton btnGenerar;
    private JButton btnDetener;
    private JProgressBar progressBar;
    private JTextArea txtLog;
    private JComboBox<String> comboDominio; // opcional: elegir dominios o aleatorio

    // Archivos por defecto (debe haberlos en la misma carpeta del .java/.class)
    private final String rutaNombresHombres = "nombres_hombres.txt";
    private final String rutaNombresMujeres = "nombres_mujeres.txt";
    private final String rutaApellidos = "apellidos.txt";
    private final String rutaUbicaciones = "ubicaciones.csv";

    // Dominios permitidos
    private final String[] dominios = {"gmail.com", "hotmail.com", "yahoo.com"};

    private void createAndShowGui() {
        frame = new JFrame("Generador de Clientes - Ecuador");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 420);
        frame.setLayout(new BorderLayout(8, 8));

        // Panel superior: entrada de cantidad y botón
        JPanel panelTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panelTop.add(new JLabel("Número de clientes (0 = sin límite):"));
        txtCantidad = new JTextField("100000", 10);
        panelTop.add(txtCantidad);

        panelTop.add(new JLabel("Dominio (opcional):"));
        comboDominio = new JComboBox<>();
        comboDominio.addItem("Aleatorio");
        comboDominio.addItem("gmail.com");
        comboDominio.addItem("hotmail.com");
        comboDominio.addItem("yahoo.com");
        panelTop.add(comboDominio);

        btnGenerar = new JButton("Generar y Guardar CSV");
        panelTop.add(btnGenerar);

        btnDetener = new JButton("Detener");
        btnDetener.setEnabled(false);
        panelTop.add(btnDetener);

        frame.add(panelTop, BorderLayout.NORTH);

        // Centro: log
        txtLog = new JTextArea();
        txtLog.setEditable(false);
        JScrollPane scroll = new JScrollPane(txtLog);
        frame.add(scroll, BorderLayout.CENTER);

        // Sur: progreso
        JPanel panelBottom = new JPanel(new BorderLayout(6,6));
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        panelBottom.add(progressBar, BorderLayout.CENTER);

        frame.add(panelBottom, BorderLayout.SOUTH);

        // Acción del botón
        btnGenerar.addActionListener(e -> onGenerarClicked());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void onGenerarClicked() {
        int cantidad;
        try {
            cantidad = Integer.parseInt(txtCantidad.getText().trim());
            // ahora 0 o negativo => sin límite
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(frame, "Ingresa un número válido de clientes (usa 0 para sin límite).", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Selector de archivo de salida
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File("clientes.csv"));
        int sel = fileChooser.showSaveDialog(frame);
        if (sel != JFileChooser.APPROVE_OPTION) return;
        File salida = fileChooser.getSelectedFile();

        // Desactivar GUI mientras se genera
        btnGenerar.setEnabled(false);
        btnDetener.setEnabled(true);
        txtLog.setText("");
        progressBar.setValue(0);

        // Ejecutar en background con SwingWorker para no bloquear la GUI
        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() {
                boolean ilimitado = (cantidad <= 0);
                try {
                    List<String> nombresHombres = leerArchivoLineas(rutaNombresHombres);
                    List<String> nombresMujeres = leerArchivoLineas(rutaNombresMujeres);
                    List<String> apellidos = leerArchivoLineas(rutaApellidos);
                    List<Ubicacion> ubicaciones = leerUbicacionesCsv(rutaUbicaciones);

                    if (nombresHombres.isEmpty() || nombresMujeres.isEmpty() || apellidos.isEmpty() || ubicaciones.isEmpty()) {
                        publish("Error: alguno de los archivos de entrada está vacío o no existe.");
                        return null;
                    }

                    Random random = new Random();

                    if (ilimitado) {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(true);
                            progressBar.setString("Generando (sin límite)...");
                        });
                    } else {
                        SwingUtilities.invokeLater(() -> {
                            progressBar.setIndeterminate(false);
                            progressBar.setMaximum(cantidad);
                        });
                    }

                    long generados = 0;
                    try (BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(salida), StandardCharsets.UTF_8))) {
                        // Encabezado CSV
                        bw.write("cedula,nombre1,nombre2,apellido1,apellido2,sexo,provincia,canton,parroquia,edad,correo");
                        bw.newLine();

                        for (long i = 0; (ilimitado || i < cantidad) && !isCancelled(); i++) {
                            boolean esHombre = random.nextBoolean();
                            String sexo = esHombre ? "Masculino" : "Femenino";

                            List<String> listaNombres = esHombre ? nombresHombres : nombresMujeres;
                            String nombre1 = elegirAleatorio(listaNombres, random);
                            String nombre2 = elegirAleatorio(listaNombres, random);
                            if (nombre2.equalsIgnoreCase(nombre1)) {
                                int attempts = 0;
                                while (attempts < 5 && nombre2.equalsIgnoreCase(nombre1)) {
                                    nombre2 = elegirAleatorio(listaNombres, random);
                                    attempts++;
                                }
                            }

                            String apellido1 = elegirAleatorio(apellidos, random);
                            String apellido2 = elegirAleatorio(apellidos, random);
                            if (apellido2.equalsIgnoreCase(apellido1)) {
                                int attempts = 0;
                                while (attempts < 5 && apellido2.equalsIgnoreCase(apellido1)) {
                                    apellido2 = elegirAleatorio(apellidos, random);
                                    attempts++;
                                }
                            }

                            Ubicacion ubic = elegirAleatorio(ubicaciones, random);

                            int edad = 18 + random.nextInt(63); // 18..80
                            String cedula = generarCedulaEcuatoriana(random);

                            String dominioSeleccionado = (String) comboDominio.getSelectedItem();
                            String dominio;
                            if ("Aleatorio".equals(dominioSeleccionado)) {
                                dominio = dominios[random.nextInt(dominios.length)];
                            } else {
                                dominio = dominioSeleccionado;
                            }

                            String correo = generarCorreoSimple(nombre1, apellido1, dominio, random);

                            bw.write(String.join(",",
                                    cedula,
                                    csvEscape(nombre1),
                                    csvEscape(nombre2),
                                    csvEscape(apellido1),
                                    csvEscape(apellido2),
                                    sexo,
                                    csvEscape(ubic.provincia),
                                    csvEscape(ubic.canton),
                                    csvEscape(ubic.parroquia),
                                    String.valueOf(edad),
                                    correo
                            ));
                            bw.newLine();

                            generados = i + 1;
                            if (generados % 1000 == 0) publish("Generados: " + generados + " ...");

                            if (!ilimitado) {
                                final int val = (int) generados;
                                SwingUtilities.invokeLater(() -> progressBar.setValue(val));
                            }
                        }
                    }

                    if (isCancelled()) {
                        publish("Generación cancelada. Generados: " + generados);
                    } else {
                        publish("Generación completada. Total generados: " + generados + ". Archivo: " + salida.getAbsolutePath());
                    }
                } catch (Exception ex) {
                    publish("Error durante la generación: " + ex.getMessage());
                    ex.printStackTrace();
                }
                return null;
            }

            @Override
            protected void process(List<String> chunks) {
                for (String msg : chunks) {
                    txtLog.append(msg + "\n");
                }
            }

            @Override
            protected void done() {
                btnGenerar.setEnabled(true);
                btnDetener.setEnabled(false);
                progressBar.setIndeterminate(false);
                progressBar.setStringPainted(true);
                progressBar.setString(null);
                txtLog.append("Proceso finalizado.\n");
                JOptionPane.showMessageDialog(frame, "Proceso finalizado.", "Listo", JOptionPane.INFORMATION_MESSAGE);
            }
        };

        // Listener para detener
        btnDetener.addActionListener(e -> {
            worker.cancel(true);
            btnDetener.setEnabled(false);
        });

        worker.execute();
    }

    // Leer líneas de un archivo txt (UTF-8), ignorando vacíos
    private List<String> leerArchivoLineas(String ruta) {
        List<String> lista = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(ruta), StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                ln = ln.trim();
                if (!ln.isEmpty()) lista.add(ln);
            }
        } catch (IOException e) {
            txtLog.append("No se pudo leer: " + ruta + "\n");
        }
        return lista;
    }

    // Leer ubicaciones desde CSV simple: provincia,canton,parroquia
    private List<Ubicacion> leerUbicacionesCsv(String ruta) {
        List<Ubicacion> lista = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(ruta), StandardCharsets.UTF_8))) {
            String ln = br.readLine(); // encabezado
            while ((ln = br.readLine()) != null) {
                if (ln.trim().isEmpty()) continue;
                String[] cols = ln.split(",", -1);
                if (cols.length >= 3) {
                    String provincia = cols[0].trim();
                    String canton = cols[1].trim();
                    String parroquia = cols[2].trim();
                    lista.add(new Ubicacion(provincia, canton, parroquia));
                }
            }
        } catch (IOException e) {
            txtLog.append("Error leyendo ubicaciones: " + e.getMessage() + "\n");
        }
        return lista;
    }

    // Elegir elemento aleatorio de lista
    private <T> T elegirAleatorio(List<T> lista, Random random) {
        return lista.get(random.nextInt(lista.size()));
    }

    // Generar una cédula ecuatoriana válida (algoritmo)
    private String generarCedulaEcuatoriana(Random random) {
        int provincia = 1 + random.nextInt(24); // 01-24
        int tercerDigito = random.nextInt(6); // 0..5
        int secuencia = random.nextInt(1000000); // 6 dígitos
        String base = String.format("%02d%d%06d", provincia, tercerDigito, secuencia); // 9 dígitos
        int verificador = calcularDigitoVerificador(base);
        return base + verificador;
    }

    private int calcularDigitoVerificador(String base) {
        int suma = 0;
        for (int i = 0; i < base.length(); i++) {
            int num = Character.getNumericValue(base.charAt(i));
            if ((i + 1) % 2 != 0) { // posición impar (1-based)
                num *= 2;
                if (num > 9) num -= 9;
            }
            suma += num;
        }
        int residuo = suma % 10;
        return residuo == 0 ? 0 : 10 - residuo;
    }

    // Generar correo simple a partir de nombre y apellido
    private String generarCorreoSimple(String nombre, String apellido, String dominio, Random random) {
        String raw = (nombre + "." + apellido).toLowerCase();
        raw = normalizarParaCorreo(raw);
        // agregar número aleatorio pequeño para reducir colisiones
        int n = 1 + random.nextInt(9999);
        return raw + n + "@" + dominio;
    }

    // Normalizar acentos y ñ para correo
    private String normalizarParaCorreo(String s) {
        s = s.replace("Á","A").replace("É","E").replace("Í","I").replace("Ó","O").replace("Ú","U");
        s = s.replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u");
        s = s.replace("Ñ","N").replace("ñ","n");
        s = s.replaceAll("[^a-z0-9\\.]", "");
        return s;
    }

    // Escapar campos CSV sencillos (añadir comillas si contiene coma)
    private String csvEscape(String campo) {
        if (campo == null) return "";
        if (campo.contains(",") || campo.contains("\"") || campo.contains("\n")) {
            return "\"" + campo.replace("\"", "\"\"") + "\"";
        } else {
            return campo;
        }
    }

    // Clase simple para ubicar provincia-canton-parroquia
    static class Ubicacion {
        String provincia;
        String canton;
        String parroquia;
        Ubicacion(String provincia, String canton, String parroquia) {
            this.provincia = provincia;
            this.canton = canton;
            this.parroquia = parroquia;
        }
        @Override
        public String toString() {
            return provincia + " / " + canton + " / " + parroquia;
        }
    }
}
