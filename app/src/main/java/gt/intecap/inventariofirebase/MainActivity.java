
package gt.intecap.inventariofirebase;

import android.os.Bundle;
import android.app.AlertDialog;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

import android.content.Intent;

public class MainActivity extends AppCompatActivity {

    private DatabaseReference databaseReference;

    private EditText etNombre, etDescripcion, etPrecio, etCantidad;
    private Button btnGuardar, btnActualizar, btnEliminar, btnLimpiar, btnDetalle, btnReportes;
    private ListView listViewProductos;

    private ArrayList<Producto> productos;
    private ArrayList<String> nombresProductos;
    private ArrayAdapter<String> adapter;

    private String idSeleccionado = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        databaseReference = FirebaseDatabase.getInstance()
                .getReference("productos");

        etNombre = findViewById(R.id.etNombre);
        etDescripcion = findViewById(R.id.etDescripcion);
        etPrecio = findViewById(R.id.etPrecio);
        etCantidad = findViewById(R.id.etCantidad);

        btnGuardar = findViewById(R.id.btnGuardar);
        btnActualizar = findViewById(R.id.btnActualizar);
        btnEliminar = findViewById(R.id.btnEliminar);
        btnLimpiar = findViewById(R.id.btnLimpiar);
        listViewProductos = findViewById(R.id.listViewProductos);

        productos = new ArrayList<>();
        nombresProductos = new ArrayList<>();

        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                nombresProductos
        );

        listViewProductos.setAdapter(adapter);

        btnGuardar.setOnClickListener(v -> guardarProducto());
        btnActualizar.setOnClickListener(v -> actualizarProducto());
        btnEliminar.setOnClickListener(v -> eliminarProducto());
        btnLimpiar.setOnClickListener(v -> limpiarFormulario());
        btnDetalle = findViewById(R.id.btnDetalle);
        btnReportes = findViewById(R.id.btnReportes);
        btnDetalle.setOnClickListener(v -> {
            if (idSeleccionado == null) {
                mensaje("Primero selecciona un producto de la lista");
                return;
            }
            Intent intent = new Intent(MainActivity.this, DetalleActivity.class);
            intent.putExtra("productoId", idSeleccionado);
            startActivity(intent);
        });
        btnReportes.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ReporteActivity.class);
            startActivity(intent);
        });
        listViewProductos.setOnItemClickListener(
                (parent, view, position, id) -> {

                    Producto producto = productos.get(position);

                    idSeleccionado = producto.getId();

                    etNombre.setText(producto.getNombre());
                    etDescripcion.setText(producto.getDescripcion());
                    etPrecio.setText(String.valueOf(producto.getPrecio()));
                    etCantidad.setText(String.valueOf(producto.getCantidad()));

                    Toast.makeText(this,
                            "Producto seleccionado",
                            Toast.LENGTH_SHORT).show();
                });

        cargarProductos();

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets systemBars = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom
                    );

                    return insets;
                });
    }

    // CREATE - GUARDAR
    private void guardarProducto() {

        Producto producto = obtenerDatosFormulario();

        if (producto == null) return;

        String id = databaseReference.push().getKey();

        if (id == null) {
            mensaje("No se pudo generar el ID");
            return;
        }

        producto.setId(id);

        databaseReference.child(id).setValue(producto)
                .addOnSuccessListener(unused -> {
                    mensaje("Producto guardado correctamente");
                    limpiarFormulario();
                })
                .addOnFailureListener(e ->
                        mensaje("Error: " + e.getMessage()));
    }

    // READ - LISTAR
    private void cargarProductos() {

        databaseReference.addValueEventListener(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        productos.clear();
                        nombresProductos.clear();

                        for (DataSnapshot dato : snapshot.getChildren()) {

                            Producto producto =
                                    dato.getValue(Producto.class);

                            if (producto != null) {

                                producto.setId(dato.getKey());

                                productos.add(producto);

                                nombresProductos.add(
                                        producto.getNombre()
                                                + "\nQ " + producto.getPrecio()
                                                + " | Cantidad: "
                                                + producto.getCantidad()
                                );
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        mensaje("Error al cargar: "
                                + error.getMessage());
                    }
                });
    }

    // UPDATE - ACTUALIZAR
    private void actualizarProducto() {

        if (idSeleccionado == null) {
            mensaje("Selecciona un producto");
            return;
        }

        Producto producto = obtenerDatosFormulario();

        if (producto == null) return;

        String id = idSeleccionado;
        producto.setId(id);

        databaseReference.child(id).setValue(producto)
                .addOnSuccessListener(unused -> {
                    mensaje("Producto actualizado");
                    limpiarFormulario();
                })
                .addOnFailureListener(e ->
                        mensaje("Error: " + e.getMessage()));
    }

    // DELETE - ELIMINAR
    private void eliminarProducto() {

        if (idSeleccionado == null) {
            mensaje("Selecciona un producto");
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Eliminar producto")
                .setMessage("¿Deseas eliminar este producto?")
                .setPositiveButton("Eliminar", (dialog, which) -> {

                    String id = idSeleccionado;

                    databaseReference.child(id).removeValue()
                            .addOnSuccessListener(unused -> {
                                mensaje("Producto eliminado");
                                limpiarFormulario();
                            })
                            .addOnFailureListener(e ->
                                    mensaje("Error: " + e.getMessage()));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // VALIDAR Y OBTENER DATOS
    private Producto obtenerDatosFormulario() {

        String nombre = etNombre.getText().toString().trim();
        String descripcion = etDescripcion.getText().toString().trim();
        String precioTexto = etPrecio.getText().toString().trim();
        String cantidadTexto = etCantidad.getText().toString().trim();

        if (nombre.isEmpty() || descripcion.isEmpty()
                || precioTexto.isEmpty() || cantidadTexto.isEmpty()) {

            mensaje("Completa todos los campos");
            return null;
        }

        try {
            double precio = Double.parseDouble(precioTexto);
            int cantidad = Integer.parseInt(cantidadTexto);

            if (!Double.isFinite(precio) || precio < 0 || cantidad < 0) {
                mensaje("Precio y cantidad deben ser válidos");
                return null;
            }

            return new Producto(
                    idSeleccionado,
                    nombre,
                    descripcion,
                    precio,
                    cantidad
            );

        } catch (NumberFormatException e) {
            mensaje("Precio o cantidad inválidos");
            return null;
        }
    }

    // LIMPIAR FORMULARIO
    private void limpiarFormulario() {

        etNombre.setText("");
        etDescripcion.setText("");
        etPrecio.setText("");
        etCantidad.setText("");

        idSeleccionado = null;
    }

    // MOSTRAR MENSAJES
    private void mensaje(String texto) {
        Toast.makeText(this, texto, Toast.LENGTH_SHORT).show();
    }




}
