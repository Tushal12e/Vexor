package com.vexor.vault.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.vexor.vault.R
import com.vexor.vault.databinding.ActivityManageVaultsBinding
import com.vexor.vault.databinding.ItemVaultBinding
import com.vexor.vault.security.VaultPreferences
import android.view.ViewGroup
import android.view.LayoutInflater
import androidx.recyclerview.widget.RecyclerView

class ManageVaultsBindingAdapter(
    private var vaults: List<VaultPreferences.VaultConfig>,
    private val onDelete: (VaultPreferences.VaultConfig) -> Unit
) : RecyclerView.Adapter<ManageVaultsBindingAdapter.VaultViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VaultViewHolder {
        val binding = ItemVaultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VaultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VaultViewHolder, position: Int) {
        holder.bind(vaults[position])
    }

    override fun getItemCount() = vaults.size

    inner class VaultViewHolder(private val binding: ItemVaultBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(vault: VaultPreferences.VaultConfig) {
            binding.tvVaultName.text = vault.name
            binding.btnDelete.setOnClickListener { onDelete(vault) }
        }
    }
}

class ManageVaultsActivity : BaseActivity() {

    private lateinit var binding: ActivityManageVaultsBinding
    private lateinit var prefs: VaultPreferences
    private lateinit var adapter: ManageVaultsBindingAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityManageVaultsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = VaultPreferences(this)
        
        setupUI()
        loadVaults()
    }
    
    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        binding.fabAdd.setOnClickListener { showAddVaultDialog() }
    }
    
    private fun loadVaults() {
        val vaults = prefs.getCustomVaults()
        adapter = ManageVaultsBindingAdapter(vaults) { vault ->
            deleteVault(vault)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }
    
    private fun showAddVaultDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }
        
        val nameInput = EditText(this).apply {
            hint = "Vault Name"
        }
        
        val pinInput = EditText(this).apply {
            hint = "Vault PIN (4-6 digits)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        }
        
        layout.addView(nameInput)
        layout.addView(pinInput)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Vault")
            .setView(layout)
            .setPositiveButton("Create") { _, _ ->
                val name = nameInput.text.toString()
                val pin = pinInput.text.toString()
                
                if (name.isNotEmpty() && pin.length >= 4) {
                    prefs.addCustomVault(name, pin)
                    loadVaults()
                    Toast.makeText(this, "Vault created!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Invalid name or PIN", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteVault(vault: VaultPreferences.VaultConfig) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Vault '${vault.name}'?")
            .setMessage("This will remove the vault access. Files associated with it might become inaccessible unless you recreate it with the EXACT SAME PIN.")
            .setPositiveButton("Delete") { _, _ ->
                prefs.deleteCustomVault(vault.id)
                loadVaults()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
