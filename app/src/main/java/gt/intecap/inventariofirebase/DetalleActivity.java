
package gt.intecap.inventariofirebase;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class DetalleActivity extends AppCompatActivity {

    private TextView tvNombreProducto, tvPrecioProducto, tvExistencias;
    private EditText etCantidadMovimiento;
    private Button btnEntrada, btnSalida, btnVolver;
    private ListView listViewMovimientos;

    private DatabaseReference productoRef;
    private DatabaseReference movimientosRef;

    private String productoId;
    private ArrayList<String> historial;
    private ArrayAdapter<String> adapter;

    private ValueEventListener productoListener;
    private ValueEventListener movimientosListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detalle);

        tvNombreProducto = findViewById(R.id.tvNombreProducto);
        tvPrecioProducto = findViewById(R.id.tvPrecioProducto);
        tvExistencias = findViewById(R.id.tvExistencias);
        etCantidadMovimiento = findViewById(R.id.etCantidadMovimiento);

        btnEntrada = findViewById(R.id.btnEntrada);
        btnSalida = findViewById(R.id.btnSalida);
        btnVolver = findViewById(R.id.btnVolver);
        listViewMovimientos = findViewById(R.id.listViewMovimientos);

        productoId = getIntent().getStringExtra("productoId");

        if (productoId == null || productoId.isEmpty()) {
            Toast.makeText(this, "No se seleccionó un producto",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        FirebaseDatabase database = FirebaseDatabase.getInstance();

        productoRef = database.getReference("productos")
                .child(productoId);

        movimientosRef = database.getReference("movimientos")
                .child(productoId);

        historial = new ArrayList<>();
        adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                historial
        );

        listViewMovimientos.setAdapter(adapter);

        btnEntrada.setOnClickListener(v -> registrarMovimiento(true));
        btnSalida.setOnClickListener(v -> registrarMovimiento(false));
        btnVolver.setOnClickListener(v -> finish());

        cargarProducto();
        cargarHistorial();
    }

    private void cargarProducto() {

        productoListener = productoRef.addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        Producto producto = snapshot.getValue(Producto.class);

                        if (producto == null) {
                            Toast.makeText(DetalleActivity.this,
                                    "El producto ya no existe",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }

                        tvNombreProducto.setText(producto.getNombre());
                        tvPrecioProducto.setText(String.format(
                                Locale.getDefault(),
                                "Precio: Q%.2f",
                                producto.getPrecio()
                        ));
                        tvExistencias.setText(
                                "Existencias: " + producto.getCantidad()
                        );
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        mostrarMensaje("Error: " + error.getMessage());
                    }
                });
    }

    private void cargarHistorial() {

        movimientosListener = movimientosRef.addValueEventListener(
                new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        historial.clear();

                        for (DataSnapshot dato : snapshot.getChildren()) {
                            String tipo = dato.child("tipo")
                                    .getValue(String.class);
                            Integer cantidad = dato.child("cantidad")
                                    .getValue(Integer.class);
                            String fecha = dato.child("fecha")
                                    .getValue(String.class);

                            if (tipo != null && cantidad != null) {
                                historial.add(0,
                                        tipo + ": " + cantidad
                                                + " unidades\n"
                                                + (fecha == null ? "" : fecha));
                            }
                        }

                        adapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        mostrarMensaje("Error al cargar historial");
                    }
                });
    }

    private void registrarMovimiento(boolean esEntrada) {

        String texto = etCantidadMovimiento.getText()
                .toString().trim();

        if (texto.isEmpty()) {
            mostrarMensaje("Ingresa una cantidad");
            return;
        }

        int cantidad;

        try {
            cantidad = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            mostrarMensaje("Cantidad inválida");
            return;
        }

        if (cantidad <= 0) {
            mostrarMensaje("La cantidad debe ser mayor que cero");
            return;
        }

        btnEntrada.setEnabled(false);
        btnSalida.setEnabled(false);

        final String tipo = esEntrada ? "ENTRADA" : "SALIDA";

        // La transacción evita sobrescribir cambios simultáneos.
        productoRef.child("cantidad").runTransaction(
                new Transaction.Handler() {

                    @Override
                    public Transaction.Result doTransaction(
                            MutableData currentData) {

                        Integer actual = currentData.getValue(Integer.class);

                        if (actual == null) {
                            return Transaction.abort();
                        }

                        long existencias = actual.longValue();
                        long nuevas = esEntrada
                                ? existencias + cantidad
                                : existencias - cantidad;

                        if (nuevas < 0 || nuevas > Integer.MAX_VALUE) {
                            return Transaction.abort();
                        }

                        currentData.setValue((int) nuevas);
                        return Transaction.success(currentData);
                    }

                    @Override
                    public void onComplete(
                            DatabaseError error,
                            boolean committed,
                            DataSnapshot snapshot) {

                        btnEntrada.setEnabled(true);
                        btnSalida.setEnabled(true);

                        if (error != null) {
                            mostrarMensaje("Error: " + error.getMessage());
                            return;
                        }

                        if (!committed) {
                            mostrarMensaje(
                                    "Movimiento rechazado. Verifica las existencias.");
                            return;
                        }

                        guardarHistorial(tipo, cantidad);
                    }
                });
    }

    private void guardarHistorial(String tipo, int cantidad) {

        String fecha = new SimpleDateFormat(
                "dd/MM/yyyy HH:mm",
                Locale.getDefault()
        ).format(new Date());

        Map<String, Object> movimiento = new HashMap<>();
        movimiento.put("tipo", tipo);
        movimiento.put("cantidad", cantidad);
        movimiento.put("fecha", fecha);

        movimientosRef.push().setValue(movimiento)
                .addOnSuccessListener(unused -> {
                    mostrarMensaje("Movimiento registrado");
                    etCantidadMovimiento.setText("");
                })
                .addOnFailureListener(e ->
                        mostrarMensaje(
                                "Existencias actualizadas, pero no se guardó "
                                        + "el historial: " + e.getMessage()));
    }

    private void mostrarMensaje(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (productoRef != null && productoListener != null) {
            productoRef.removeEventListener(productoListener);
        }

        if (movimientosRef != null && movimientosListener != null) {
            movimientosRef.removeEventListener(movimientosListener);
        }
    }
}

