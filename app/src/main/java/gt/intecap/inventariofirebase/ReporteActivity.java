
package gt.intecap.inventariofirebase;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Locale;

public class ReporteActivity extends AppCompatActivity {

    private TextView tvTotalProductos;
    private TextView tvTotalUnidades;
    private TextView tvValorInventario;

    private EditText etBuscarProducto;
    private ListView listViewBajoStock;
    private ListView listViewResultados;
    private Button btnVolverProductos;

    private DatabaseReference productosRef;
    private ValueEventListener productosListener;

    private final ArrayList<Producto> productos = new ArrayList<>();
    private final ArrayList<String> bajoStock = new ArrayList<>();
    private final ArrayList<String> resultados = new ArrayList<>();

    private ArrayAdapter<String> adapterBajoStock;
    private ArrayAdapter<String> adapterResultados;

    private static final int LIMITE_BAJO_STOCK = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reporte);

        tvTotalProductos = findViewById(R.id.tvTotalProductos);
        tvTotalUnidades = findViewById(R.id.tvTotalUnidades);
        tvValorInventario = findViewById(R.id.tvValorInventario);

        etBuscarProducto = findViewById(R.id.etBuscarProducto);
        listViewBajoStock = findViewById(R.id.listViewBajoStock);
        listViewResultados = findViewById(R.id.listViewResultados);
        btnVolverProductos = findViewById(R.id.btnVolverProductos);

        adapterBajoStock = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                bajoStock
        );

        adapterResultados = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                resultados
        );

        listViewBajoStock.setAdapter(adapterBajoStock);
        listViewResultados.setAdapter(adapterResultados);

        btnVolverProductos.setOnClickListener(v -> finish());

        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(
                    CharSequence s, int start, int before, int count) {
                filtrarProductos(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        productosRef = FirebaseDatabase.getInstance()
                .getReference("productos");

        cargarReporte();
    }

    private void cargarReporte() {

        productosListener = productosRef.addValueEventListener(
                new ValueEventListener() {

                    @Override
                    public void onDataChange(DataSnapshot snapshot) {

                        productos.clear();

                        long totalUnidades = 0;
                        double valorInventario = 0;

                        bajoStock.clear();

                        for (DataSnapshot dato : snapshot.getChildren()) {

                            Producto producto = dato.getValue(Producto.class);

                            if (producto == null) {
                                continue;
                            }

                            productos.add(producto);

                            int cantidad = producto.getCantidad();
                            double precio = producto.getPrecio();

                            totalUnidades += cantidad;
                            valorInventario += precio * cantidad;

                            if (cantidad <= LIMITE_BAJO_STOCK) {
                                bajoStock.add(
                                        producto.getNombre()
                                                + " — " + cantidad
                                                + " unidades"
                                );
                            }
                        }

                        tvTotalProductos.setText(
                                "Productos\n" + productos.size()
                        );

                        tvTotalUnidades.setText(
                                "Unidades\n" + totalUnidades
                        );

                        tvValorInventario.setText(
                                String.format(
                                        Locale.getDefault(),
                                        "Valor\nQ %,.2f",
                                        valorInventario
                                )
                        );

                        if (bajoStock.isEmpty()) {
                            bajoStock.add(
                                    "No hay productos con pocas existencias"
                            );
                        }

                        adapterBajoStock.notifyDataSetChanged();

                        filtrarProductos(
                                etBuscarProducto.getText().toString()
                        );
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        Toast.makeText(
                                ReporteActivity.this,
                                "Error al cargar reporte: "
                                        + error.getMessage(),
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    private void filtrarProductos(String busqueda) {

        resultados.clear();

        String filtro = busqueda.trim().toLowerCase(Locale.ROOT);

        for (Producto producto : productos) {

            String nombre = producto.getNombre();

            if (nombre != null
                    && nombre.toLowerCase(Locale.ROOT).contains(filtro)) {

                resultados.add(
                        nombre
                                + "\nPrecio: "
                                + String.format(
                                Locale.getDefault(),
                                "Q %.2f",
                                producto.getPrecio()
                        )
                                + " | Existencias: "
                                + producto.getCantidad()
                );
            }
        }

        if (resultados.isEmpty()) {
            resultados.add("No se encontraron productos");
        }

        adapterResultados.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (productosRef != null && productosListener != null) {
            productosRef.removeEventListener(productosListener);
        }
    }
}
