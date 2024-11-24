package org.scottishtecharmy.soundscape.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.scottishtecharmy.soundscape.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSearchBar(
    searchText: String,
    isSearching: Boolean,
    itemList: List<SearchItem>,
    onSearchTextChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onItemClick: (SearchItem) -> Unit
) {
    SearchBar(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (!isSearching) {
                    Modifier.padding(horizontal = 16.dp)
                } else {
                    Modifier.padding(horizontal = 0.dp)
                }
            ),
        shape = RoundedCornerShape(8.dp),
        colors = SearchBarDefaults.colors(containerColor = Color.White),
        inputField = {
            SearchBarDefaults.InputField(
                query = searchText,
                onQueryChange = onSearchTextChange,
                onSearch = onSearchTextChange,
                expanded = isSearching,
                onExpandedChange = { onToggleSearch()  },
                placeholder = { Text(stringResource(id = R.string.search_hint_input)) },
                leadingIcon = {
                    when {
                        !isSearching -> {
                            Icon(
                                Icons.Rounded.Search,
                                null,
                                tint = Color.Gray
                            )
                        }
                        else -> {
                            IconButton(
                                onClick = { onToggleSearch() },
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Cancel search",
                                    tint = Color.Gray
                                )
                            }
                        }
                    }
                },
                trailingIcon = { Icon(Icons.Default.MoreVert, contentDescription = null) },
                colors = SearchBarDefaults.inputFieldColors(focusedTextColor = Color.Black)

            )
        },
        expanded = isSearching,
        onExpandedChange = { onToggleSearch()  },
    ){
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(itemList) { item ->
                    SearchItemButton(
                        item = item,
                        onClick = {
                            onItemClick(item)
                        },
                        modifier = Modifier
                    )
                }
            }
        }
    }

}