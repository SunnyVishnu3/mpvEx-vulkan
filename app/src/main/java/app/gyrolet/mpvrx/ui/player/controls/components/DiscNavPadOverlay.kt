/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package app.gyrolet.mpvrx.ui.player.controls.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.gyrolet.mpvrx.R
import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons
import app.gyrolet.mpvrx.ui.player.PlaybackSession

@Composable
fun DiscNavPadOverlay(
  modifier: Modifier = Modifier,
) {
  val isDiscMenuActive by PlaybackSession.propBoolean["disc-menu-active"].collectAsState()
  val discTitles by PlaybackSession.propInt["disc-titles"].collectAsState()

  val showNavPad = isDiscMenuActive == true

  AnimatedVisibility(
    visible = showNavPad,
    enter = fadeIn() + scaleIn(),
    exit = fadeOut() + scaleOut(),
    modifier = modifier,
  ) {
    Box(
      modifier = Modifier
        .clip(RoundedCornerShape(24.dp))
        .background(Color.Black.copy(alpha = 0.65f))
        .padding(12.dp),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        // UP
        FilledIconButton(
          onClick = { PlaybackSession.command("discnav", "up") },
          modifier = Modifier.size(44.dp),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
          ),
        ) {
          Icon(Icons.RoundedFilled.KeyboardArrowUp, contentDescription = stringResource(R.string.disc_menu_up))
        }

        // LEFT - SELECT - RIGHT
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconButton(
            onClick = { PlaybackSession.command("discnav", "left") },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.18f),
              contentColor = Color.White,
            ),
          ) {
            Icon(Icons.RoundedFilled.KeyboardArrowLeft, contentDescription = stringResource(R.string.disc_menu_left))
          }

          FilledIconButton(
            onClick = { PlaybackSession.command("discnav", "select") },
            modifier = Modifier.size(48.dp),
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
          ) {
            Icon(Icons.RoundedFilled.Check, contentDescription = stringResource(R.string.disc_menu_select))
          }

          FilledIconButton(
            onClick = { PlaybackSession.command("discnav", "right") },
            modifier = Modifier.size(44.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.18f),
              contentColor = Color.White,
            ),
          ) {
            Icon(Icons.RoundedFilled.KeyboardArrowRight, contentDescription = stringResource(R.string.disc_menu_right))
          }
        }

        // DOWN
        FilledIconButton(
          onClick = { PlaybackSession.command("discnav", "down") },
          modifier = Modifier.size(44.dp),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
          ),
        ) {
          Icon(Icons.RoundedFilled.KeyboardArrowDown, contentDescription = stringResource(R.string.disc_menu_down))
        }

        // PREV / MENU action row
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          FilledIconButton(
            onClick = { PlaybackSession.command("discnav", "prev") },
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.12f),
              contentColor = Color.White,
            ),
          ) {
            Icon(Icons.RoundedFilled.ArrowBack, contentDescription = stringResource(R.string.disc_menu_prev))
          }

          FilledIconButton(
            onClick = { PlaybackSession.command("discnav", "menu") },
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = Color.White.copy(alpha = 0.12f),
              contentColor = Color.White,
            ),
          ) {
            Icon(Icons.RoundedFilled.Movie, contentDescription = stringResource(R.string.disc_menu_button))
          }
        }
      }
    }
  }
}

